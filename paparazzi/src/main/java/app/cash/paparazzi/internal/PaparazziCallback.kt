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

import app.cash.paparazzi.Environment
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.AssetRepository
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.SessionParams.Key
import com.android.ide.common.resources.deprecated.ResourceItem
import com.android.ide.common.resources.deprecated.ResourceRepository
import com.android.io.FolderWrapper
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.resources.ResourceType
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.lang.reflect.Modifier

internal fun readResources(logger: PaparazziLogger, rClass: Class<*>, resourceNamespace: ResourceNamespace) : Map<Int, ResourceReference> {
    val resourcesMap = mutableMapOf<Int, ResourceReference>()
    for (resourceClass in rClass.declaredClasses) {
        val resourceType = ResourceType.getEnum(resourceClass.simpleName) ?: continue
        if (resourceType == ResourceType.STYLEABLE) continue//these are not resources, just handy little helpers

        for (field in resourceClass.declaredFields) {
            if (!Modifier.isStatic(field.modifiers)) continue

            // May not be final in library projects.
            val type = field.type
            try {
                if (type == Int::class.javaPrimitiveType) {
                    val value = field.get(null) as Int
                    val reference = ResourceReference(resourceNamespace, resourceType, field.name.let {
                        when(resourceType) {
                            ResourceType.STYLE -> it.replace("_", ".")
                            else -> it
                        }
                    })
                    resourcesMap[value] = reference

                } else if (type.isArray && type.componentType == Int::class.javaPrimitiveType) {
                    // Ignore.
                } else {
                    logger.error(null, "Unknown field type in R class: $type")
                }
            } catch (e: IllegalAccessException) {
                logger.error(e, "Malformed R class: %1\$s", rClass.name)
            }
        }
    }
    return resourcesMap
}

internal open class PaparazziCallback(
        protected val logger: PaparazziLogger,
        protected val environment: Environment,
        protected val projectResources: Map<Int, ResourceReference> = readResources(logger, Class.forName("${environment.packageName}.R"), RES_AUTO)
) : LayoutlibCallback() {
    private val resources = projectResources.entries.associateBy({ it.value }) { it.key }
    private val actionBarCallback = ActionBarCallback()

    private var adaptiveIconMaskPath: String? = null
    private var highQualityShadow = false
    private var enableShadow = true

    @Throws(ClassNotFoundException::class)
    open fun initResources() {
    }

    @Throws(Exception::class)
    override fun loadView(
            name: String,
            constructorSignature: Array<Class<*>>,
            constructorArgs: Array<Any>
    ): Any? {
        val viewClass = Class.forName(name)
        val viewConstructor = viewClass.getConstructor(*constructorSignature)
        viewConstructor.isAccessible = true
        return viewConstructor.newInstance(*constructorArgs)
    }

    override fun getNamespace(): String =
            String.format(SdkConstants.NS_CUSTOM_RESOURCES_S, environment.packageName)

    override fun resolveResourceId(id: Int): ResourceReference? {
        return projectResources[id].also {
            println("resolveResourceId for ID $id is ${it?.name} ${it?.resourceType} ${it?.resourceUrl}")
        }
    }

    override fun getOrGenerateResourceId(resource: ResourceReference): Int {
        return (resources[resource] ?: 0).also {
            println("getOrGenerateResourceId for ${resource.name} ${resource.resourceType} ${resource.resourceUrl} is ID $it")
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

    override fun getAdapterBinding(
            adapterViewRef: ResourceReference,
            adapterCookie: Any,
            viewObject: Any
    ): AdapterBinding? = null

    override fun getActionBarCallback(): ActionBarCallback = actionBarCallback

    override fun supports(ideFeature: Int): Boolean = false

    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser? =
            createXmlParserForFile(fileName)

    override fun createXmlParser(): XmlPullParser = KXmlParser()

    override fun <T> getFlag(key: Key<T>?): T? {
        return when (key) {
            RenderParamsFlags.FLAG_KEY_APPLICATION_PACKAGE -> environment.packageName as T
            RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH -> adaptiveIconMaskPath as T?
            RenderParamsFlags.FLAG_RENDER_HIGH_QUALITY_SHADOW -> highQualityShadow as T
            RenderParamsFlags.FLAG_ENABLE_SHADOW -> enableShadow as T
            else -> null
        }
    }

    override fun createXmlParserForFile(fileName: String): XmlPullParser? {
        File(fileName).run {
            return if (exists()) {
                KXmlParser().also {
                    it.setInput(this.reader())
                    it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                }
            } else {
                null
            }
        }
    }

    override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? {
        File(layoutResource.value).run {
            return if (exists()) {
                LayoutPullParser.createFromFile(this)
            } else {
                null
            }
        }
    }

    fun createProjectResources(): ResourceRepository {
        return object : ResourceRepository(FolderWrapper(environment.resDir), false) {
            override fun createResourceItem(name: String): ResourceItem {
                return ResourceItem(name)
            }
        }
    }

    fun createAssetsRepository(): AssetRepository = PaparazziAssetRepository(environment.assetsDir)

    fun setAdaptiveIconMaskPath(adaptiveIconMaskPath: String) {
        this.adaptiveIconMaskPath = adaptiveIconMaskPath
    }

    fun setHighQualityShadow(highQualityShadow: Boolean) {
        this.highQualityShadow = highQualityShadow
    }

    fun setEnableShadow(enableShadow: Boolean) {
        this.enableShadow = enableShadow
    }
}
