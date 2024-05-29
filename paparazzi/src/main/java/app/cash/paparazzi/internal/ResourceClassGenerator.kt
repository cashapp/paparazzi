package app.cash.paparazzi.internal

import com.android.SdkConstants
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.resources.getFieldNameByResourceName
import com.google.common.collect.Lists
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.BIPUSH
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.IASTORE
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.NEWARRAY
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.SIPUSH
import org.objectweb.asm.Opcodes.T_INT
import org.objectweb.asm.Opcodes.V1_6
import org.objectweb.asm.Type
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The [ResourceClassGenerator] can generate R classes on the fly for a given resource repository.
 *
 * This is used to supply R classes on demand for layoutlib in order to render custom views in AAR libraries,
 * since AAR libraries ship with the view classes but with the R classes stripped out (this is done deliberately
 * such that the actual resource id's can be computed at build time by the app using the AAR resources; otherwise
 * there could be id collisions.
 *
 * However, note that the custom view code itself does not know what the actual application R class package will
 * be - and we don't rewrite bytecode at build time to tell it. Instead, the build system will generate multiple
 * R classes, one for each AAR (in addition to the real R class), and it will pick unique id's for all the
 * resources across the whole app, and then writes these unique id's into the R class for each AAR as well.
 *
 * It is **that** R class we are generating on the fly here. We want to be able to render custom views even
 * if the full application has not been compiled yet, so if normal class loading fails to identify a R class,
 * this generator will be called. It uses the normal resource repository (already used during rendering to
 * look up resources such as string and style values), and based on the names there generates bytecode on the
 * fly which can then be loaded into the VM and handled by the class loader.
 *
 * In non-namespaced projects, the R class for an aar should contain the resource references to resources from
 * the aar and all its dependencies. It is not straight-forward to get the list of dependencies after the creation
 * of the resource repositories for each aar. So, we use the app's resource repository and generate the R file
 * from it. This will break custom libraries that use reflection on the R class, but meh.
 *
 * In namespaced projects the R class contains only resources from the aar itself and the repository used by the
 * [ResourceClassGenerator] should be the one created from the AAR.
 */
public class ResourceClassGenerator private constructor(
  private val idProvider: NumericIdProvider,
  private val resources: ResourceRepository,
  private val namespace: ResourceNamespace
) {
  public interface NumericIdProvider {
    /**
     * Counter that tracks when the provider has been reset. This counter will be increased in every reset.
     * If the ids returned by [getOrGenerateId] are being cached, they must be invalidated when
     * the generation changes.
     */
    public val generation: Long
    public fun getOrGenerateId(resourceReference: ResourceReference): Int
  }

  private var idGeneratorGeneration = -1L
  private var myCache: MutableMap<ResourceType, MutableMap<String, Int>>? = null

  /** For int[] in styleables. The ints in styleables are stored in [myCache].  */
  private var styleableCache: MutableMap<String, ArrayList<Int>>? = null

  /**
   * @param fqcn Fully qualified class name (as accepted by ClassLoader, or as returned by Class.getName())
   */
  public fun generate(fqcn: String): ByteArray? {
    val className = fqcn.replace('.', '/')
    if (LOG.isLoggable(Level.INFO)) {
      LOG.info("generate($className)")
    }
    val cw = ClassWriter(0) // Don't compute MAXS and FRAMES.
    cw.visit(
      /* version = */ V1_6,
      /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
      /* name = */ className,
      /* signature = */ null,
      /* superName = */ Type.getInternalName(Any::class.java),
      /* interfaces = */ null
    )
    val index = className.lastIndexOf('$')
    if (index != -1) {
      val typeName = className.substring(index + 1)
      val type = ResourceType.fromClassName(typeName)
      if (type == null) {
        if (LOG.isLoggable(Level.INFO)) {
          LOG.info("  type '$typeName' doesn't exist")
        }
        return null
      }
      cw.visitInnerClass(
        /* name = */ className,
        /* outerName = */ className.substring(0, index),
        /* innerName = */ typeName,
        /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_STATIC
      )
      val currentIdGeneration = idProvider.generation
      if (idGeneratorGeneration != currentIdGeneration || myCache == null) {
        myCache = mutableMapOf()
        styleableCache = null
        idGeneratorGeneration = currentIdGeneration
      }
      if (type == ResourceType.STYLEABLE) {
        if (styleableCache == null) {
          myCache!![ResourceType.STYLEABLE] = HashMap()
          styleableCache = mutableMapOf()
          generateStyleable(cw, className)
        } else {
          val indexFieldsCache = myCache!![ResourceType.STYLEABLE]!!
          generateFields(cw, indexFieldsCache)
          generateIntArraysFromCache(cw, className)
        }
      } else {
        var typeCache = myCache!![type]
        if (typeCache == null) {
          typeCache = mutableMapOf()
          myCache!![type] = typeCache
          generateValuesForType(cw, type, typeCache)
        } else {
          generateFields(cw, typeCache)
        }
      }
    } else {
      // Default R class.
      for (t in resources.getResourceTypes(namespace)) {
        if (t.hasInnerClass) {
          cw.visitInnerClass(
            /* name = */ className + "$" + t.getName(),
            /* outerName = */ className,
            /* innerName = */ t.getName(),
            /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_STATIC
          )
        }
      }
    }

    generateConstructor(cw)
    cw.visitEnd()
    return cw.toByteArray()
  }

  private fun generateValuesForType(
    cw: ClassWriter,
    resType: ResourceType,
    cache: MutableMap<String, Int>
  ) {
    val resourceNames = resources.getResourceNames(namespace, resType)
    for (resName in resourceNames) {
      val initialValue = idProvider.getOrGenerateId(ResourceReference(namespace, resType, resName))
      val fieldName = getFieldNameByResourceName(resName)
      generateField(cw, fieldName, initialValue)
      cache[fieldName] = initialValue
    }
  }

  private fun generateStyleable(cw: ClassWriter, className: String) {
    if (LOG.isLoggable(Level.INFO)) {
      LOG.info(String.format("generateStyleable(%s)", className))
    }
    val debug = LOG.isLoggable(Level.INFO) && isPublicClass(className)
    val indexFieldsCache = myCache!![ResourceType.STYLEABLE]!!
    val styleableNames: Collection<String> =
      resources.getResourceNames(namespace, ResourceType.STYLEABLE)
    val mergedStyleables = ArrayList<MergedStyleable>(styleableNames.size)

    // Generate all declarations - both int[] and int for the indices into the array.
    for (styleableName in styleableNames) {
      val items = resources.getResources(namespace, ResourceType.STYLEABLE, styleableName)
      if (items.isEmpty()) {
        if (debug) {
          LOG.info("  No items for $styleableName")
        }
        continue
      }
      val fieldName = getFieldNameByResourceName(styleableName)
      cw.visitField(
        /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
        /* name = */ fieldName,
        /* descriptor = */ "[I",
        /* signature = */ null,
        /* value = */ null
      )
      if (debug) {
        LOG.info("  Defined styleable $fieldName")
      }

      // Merge all the styleables with the same name, to compute the sum of all attrs defined in them.
      val mergedAttributes = LinkedHashSet<ResourceReference>()
      for (item in items) {
        mergedAttributes.addAll(getStyleableAttributes(item))
      }
      mergedStyleables.add(MergedStyleable(styleableName, mergedAttributes))
      for ((idx, attr) in mergedAttributes.withIndex()) {
        val styleableEntryName = getResourceName(fieldName, attr)
        cw.visitField(
          /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
          /* name = */ styleableEntryName,
          /* descriptor = */ "I",
          /* signature = */ null,
          /* value = */ idx
        )
        indexFieldsCache[styleableEntryName] = idx
        if (debug) {
          LOG.info("  Defined styleable $styleableEntryName")
        }
      }
    }

    // Generate class initializer block to initialize the arrays declared above.
    val mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)
    mv.visitCode()
    for (mergedStyleable in mergedStyleables) {
      val fieldName = getFieldNameByResourceName(mergedStyleable.name)
      val values = ArrayList<Int>()
      for (attr in mergedStyleable.attrs) {
        values.add(idProvider.getOrGenerateId(attr))
      }
      styleableCache!![fieldName] = values
      generateArrayInitialization(mv, className, fieldName, values)
    }
    mv.visitInsn(RETURN)
    mv.visitMaxs(4, 0)
    mv.visitEnd()
  }

  private fun isPublicClass(className: String?): Boolean {
    var className = className ?: return false
    className = className.replace("/", ".")
    return className.startsWith("java.") || className.startsWith("javax.") ||
      className.startsWith(SdkConstants.ANDROID_SUPPORT_ARTIFACT_PREFIX) ||
      className.startsWith(SdkConstants.ANDROID_PKG_PREFIX) ||
      className.startsWith("com.google.")
  }

  private fun generateIntArraysFromCache(cw: ClassWriter, className: String) {
    // Generate the field declarations.
    for (name in styleableCache!!.keys) {
      cw.visitField(
        /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
        /* name = */ name,
        /* descriptor = */ "[I",
        /* signature = */ null,
        /* value = */ null
      )
    }

    // Generate class initializer block to initialize the arrays declared above.
    val mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null)
    mv.visitCode()
    styleableCache!!.forEach { (arrayName: String, values: ArrayList<Int>) ->
      generateArrayInitialization(mv, className, arrayName, values)
    }
    mv.visitInsn(RETURN)
    mv.visitMaxs(4, 0)
    mv.visitEnd()
  }

  private fun getResourceName(styleableName: String?, value: ResourceReference): String {
    val sb = StringBuilder(30)
    sb.append(styleableName)
    sb.append('_')
    if (value.namespace != namespace) {
      val packageName = value.namespace.packageName
      if (packageName != null) {
        appendEscaped(sb, packageName)
        sb.append('_')
      }
    }
    appendEscaped(sb, value.name)
    return sb.toString()
  }

  private class MergedStyleable(val name: String, val attrs: LinkedHashSet<ResourceReference>)

  public companion object {
    private val LOG = Logger.getLogger(ResourceClassGenerator::class.java.getName())

    public fun create(
      manager: NumericIdProvider,
      resources: ResourceRepository,
      namespace: ResourceNamespace
    ): ResourceClassGenerator = ResourceClassGenerator(manager, resources, namespace)

    /**
     * Returns the list of [ResourceReference] to attributes declared in the given styleable resource item.
     */
    private fun getStyleableAttributes(item: ResourceItem): List<ResourceReference> {
      val resourceValue = item.resourceValue
      assert(resourceValue is StyleableResourceValue)
      val dv = resourceValue as StyleableResourceValue
      return Lists.transform(dv.allAttributes) { obj: AttrResourceValue -> obj.asReference() }
    }

    private fun generateFields(cw: ClassWriter, values: Map<String, Int>) {
      values.forEach { (name: String, value: Int) -> generateField(cw, name, value) }
    }

    private fun generateField(cw: ClassWriter, name: String, value: Int) {
      cw.visitField(
        /* access = */ ACC_PUBLIC + ACC_FINAL + ACC_STATIC,
        /* name = */ name,
        /* descriptor = */ "I",
        /* signature = */ null,
        /* value = */ value
      ).visitEnd()
    }

    /**
     * Generates the instruction to push value into the stack. It will select the best opcode depending on the given value.
     */
    private fun pushIntValue(mv: MethodVisitor, value: Int) {
      if (value >= -1 && value <= 5) {
        mv.visitInsn(ICONST_0 + value)
      } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
        mv.visitIntInsn(BIPUSH, value)
      } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
        mv.visitIntInsn(SIPUSH, value)
      } else {
        mv.visitLdcInsn(value)
      }
    }

    /**
     * Generate code to put set the initial values of an array field (for styleables).
     * @param mv the class initializer's MethodVisitor (&lt;clinit&gt;)
     */
    private fun generateArrayInitialization(
      mv: MethodVisitor, className: String, fieldName: String, values: ArrayList<Int>
    ) {
      if (values.isEmpty()) {
        return
      }
      pushIntValue(mv, values.size)
      mv.visitIntInsn(NEWARRAY, T_INT)
      for (idx in values.indices) {
        mv.visitInsn(DUP)
        pushIntValue(mv, idx)
        mv.visitLdcInsn(values[idx])
        mv.visitInsn(IASTORE)
      }
      mv.visitFieldInsn(PUTSTATIC, className, fieldName, "[I")
    }

    /** Generate an empty constructor.  */
    private fun generateConstructor(cw: ClassWriter) {
      val mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
      mv.visitCode()
      mv.visitVarInsn(ALOAD, 0)
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
      mv.visitInsn(RETURN)
      mv.visitMaxs(1, 1)
      mv.visitEnd()
    }

    private fun appendEscaped(sb: StringBuilder, v: String) {
      // See RClassNaming.getFieldNameByResourceName
      var i = 0
      val n = v.length
      while (i < n) {
        val c = v[i]
        if (c == '.' || c == ':' || c == '-') {
          sb.append('_')
        } else {
          sb.append(c)
        }
        i++
      }
    }
  }
}
