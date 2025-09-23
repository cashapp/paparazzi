/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.parsers.LayoutPullParser
import app.cash.paparazzi.internal.parsers.TagSnapshot
import com.android.AndroidXConstants.CLASS_RECYCLER_VIEW_ADAPTER
import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.android.resources.ResourceType.STYLE
import com.google.common.io.ByteStreams
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.reflect.Modifier

internal class PaparazziCallback(
  private val logger: PaparazziLogger,
  private val packageName: String,
  private val resourcePackageNames: List<String>
) : LayoutlibCallback() {
  private val projectResources = mutableMapOf<Int, ResourceReference>()
  private val resources = mutableMapOf<ResourceReference, Int>()
  private val actionBarCallback = ActionBarCallback()
  private val aaptDeclaredResources = mutableMapOf<String, TagSnapshot>()
  private val dynamicResourceIdManager = DynamicResourceIdManager()

  private val loadedClasses = mutableMapOf<String, Class<*>>()

  @Throws(ClassNotFoundException::class)
  fun initResources() {
    for (rPackageName in resourcePackageNames) {
      val rClass = try {
        Class.forName("$rPackageName.R")
      } catch (e: ClassNotFoundException) {
        if (rPackageName in KNOWN_PACKAGES_WITHOUT_R_CLASS) continue
        throw e
      }

      for (resourceClass in rClass.declaredClasses) {
        val resourceType = ResourceType.fromClassName(resourceClass.simpleName) ?: continue

        for (field in resourceClass.declaredFields) {
          if (!Modifier.isStatic(field.modifiers) || field.isSynthetic) continue

          // May not be final in library projects.
          val type = field.type
          try {
            if (type == Int::class.javaPrimitiveType) {
              val value = field.get(null) as Int
              val reference = ResourceReference(RES_AUTO, resourceType, field.name)
              projectResources[value] = reference
              resources[reference] = value
            } else if (type.isArray && type.componentType == Int::class.javaPrimitiveType) {
              // Ignore.
            } else {
              logger.error(null, "Unknown type ($type) in R class field: $field")
            }
          } catch (e: IllegalAccessException) {
            logger.error(e, "Malformed R class: %1\$s", "$rPackageName.R")
          }
        }
      }
    }
  }

  @Throws(Exception::class)
  override fun loadView(name: String, constructorSignature: Array<Class<*>>, constructorArgs: Array<Any>): Any? =
    createNewInstance(name, constructorSignature, constructorArgs)

  override fun loadClass(name: String, constructorSignature: Array<Class<*>>, constructorArgs: Array<Any>): Any? {
    // RecyclerView.Adapter is an abstract class, but its instance is needed for RecyclerView to work correctly.
    // So, when LayoutLib asks for its instance, we define a new class which extends the Adapter class.
    // We check whether the class being loaded is the support or the androidx one and use the appropriate adapter that references to the
    // right namespace.
    return try {
      when (name) {
        CLASS_RECYCLER_VIEW_ADAPTER.newName() -> {
          createNewInstance(CN_ANDROIDX_CUSTOM_ADAPTER, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY)
        }
        CLASS_RECYCLER_VIEW_ADAPTER.oldName() -> {
          createNewInstance(CN_SUPPORT_CUSTOM_ADAPTER, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY)
        }
        else -> {
          createNewInstance(name, constructorSignature, constructorArgs)
        }
      }
    } catch (e: ClassNotFoundException) {
      null
    }
  }

  override fun resolveResourceId(id: Int): ResourceReference? =
    projectResources[id] ?: dynamicResourceIdManager.findById(id)

  override fun getOrGenerateResourceId(resource: ResourceReference): Int {
    // Workaround: We load our resource map from fields in R.class, which are named using Java
    // class conventions.  Therefore, we need to similarly transform style naming conventions
    // that contain periods (e.g., Widget.AppCompat.TextView) to avoid false lookup misses.
    // Long-term: Perhaps parse and load resource names from file system directly?
    val resourceKey =
      if (resource.resourceType == STYLE) resource.transformStyleResource() else resource
    return resources[resourceKey] ?: dynamicResourceIdManager.getOrGenerateId(resourceKey)
  }

  override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? {
    try {
      val value = layoutResource.value ?: return null
      if (aaptDeclaredResources.isNotEmpty() && layoutResource.resourceType == ResourceType.AAPT) {
        val aaptResource = aaptDeclaredResources.getValue(value)
        return LayoutPullParser.createFromAaptResource(aaptResource)
      }

      return LayoutPullParser.createFromFile(File(layoutResource.value))
        .also {
          // For parser of elements included in this parser, publish any aapt declared values
          aaptDeclaredResources.putAll(it.getAaptDeclaredAttrs())
        }
    } catch (e: FileNotFoundException) {
      return null
    }
  }

  override fun getAdapterItemValue(
    adapterView: ResourceReference,
    adapterCookie: Any,
    itemRef: ResourceReference,
    fullPosition: Int,
    positionPerType: Int,
    fullParentPosition: Int,
    parentPositionPerType: Int,
    viewRef: ResourceReference,
    viewAttribute: ViewAttribute,
    defaultValue: Any
  ): Any? = null

  override fun getAdapterBinding(viewObject: Any?, attributes: MutableMap<String, String>?): AdapterBinding? = null

  override fun getActionBarCallback(): ActionBarCallback = actionBarCallback

  override fun createXmlParserForPsiFile(fileName: String): XmlPullParser? = createXmlParserForFile(fileName)

  override fun createXmlParserForFile(fileName: String): XmlPullParser? {
    try {
      FileInputStream(fileName).use { fileStream ->
        // Read data fully to memory to be able to close the file stream.
        val byteOutputStream = ByteArrayOutputStream()
        ByteStreams.copy(fileStream, byteOutputStream)
        val parser = KXmlParser()
        parser.setInput(ByteArrayInputStream(byteOutputStream.toByteArray()), null)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        return parser
      }
    } catch (e: IOException) {
      return null
    } catch (e: XmlPullParserException) {
      return null
    }
  }

  override fun createXmlParser(): XmlPullParser = KXmlParser()

  override fun getApplicationId(): String = packageName

  override fun getResourcePackage(): String = packageName

  override fun findClass(name: String): Class<*> {
    val clazz = loadedClasses[name]
    logger.verbose("loadClassA($name)")

    try {
      if (clazz != null) {
        return clazz
      }
      val clazz2 = Class.forName(name)
      logger.verbose("loadClassB($name)")
      loadedClasses[name] = clazz2
      return clazz2
    } catch (e: LinkageError) {
      throw ClassNotFoundException("error loading class $name", e)
    } catch (e: ExceptionInInitializerError) {
      throw ClassNotFoundException("error loading class $name", e)
    } catch (e: ClassNotFoundException) {
      throw ClassNotFoundException("error loading class $name", e)
    }
  }

  private fun ResourceReference.transformStyleResource() = ResourceReference.style(namespace, name.replace('.', '_'))

  private fun createNewInstance(
    name: String,
    constructorSignature: Array<Class<*>>,
    constructorArgs: Array<Any>
  ): Any? {
    val anyClass = Class.forName(name)
    val anyConstructor = anyClass.getConstructor(*constructorSignature)
    anyConstructor.isAccessible = true
    return anyConstructor.newInstance(*constructorArgs)
  }

  private companion object {
    private val EMPTY_CLASS_ARRAY = emptyArray<Class<*>>()
    private val EMPTY_OBJECT_ARRAY = emptyArray<Any>()
    private const val CN_ANDROIDX_CUSTOM_ADAPTER = "com.android.layoutlib.bridge.android.androidx.Adapter"
    private const val CN_SUPPORT_CUSTOM_ADAPTER = "com.android.layoutlib.bridge.android.support.Adapter"

    /**
     * Some special AndroidX packages don't publish an R.class. These packages have no resources so it's safe to skip
     * them. (But if a new release starts publishing an R.class, we should honor it.)
     */
    private val KNOWN_PACKAGES_WITHOUT_R_CLASS = setOf("androidx.legacy.coreutils")
  }
}
