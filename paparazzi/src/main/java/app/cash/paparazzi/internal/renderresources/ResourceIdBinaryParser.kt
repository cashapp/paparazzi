package app.cash.paparazzi.internal.renderresources

import com.android.annotations.TestOnly
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Data class representing an R class.
 *
 * @param name The name of the R class.
 * @param declaredClassesIndex A map of declared classes, keyed by their names.
 * @param declaredFieldsIndex A map of declared fields, keyed by their names.
 */
internal data class ResourceClass(
  val name: String,
  val declaredClassesIndex: Map<String, ResourceClass>,
  val declaredFieldsIndex: Map<String, Field>,
  val hasUnresolvedFields: Boolean,
) {
  @TestOnly
  constructor(
    name: String,
    declaredClasses: List<ResourceClass> = emptyList(),
    declaredFields: List<Field> = emptyList(),
  ) : this(
    name,
    declaredClasses.associateBy { it.name },
    declaredFields.associateBy { it.name },
    declaredFields.any { it is Field.UnresolvedInt },
  )

  /** [ResourceClass]es contained in this [ResourceClass]. */
  val declaredClasses: List<ResourceClass>
    get() = declaredClassesIndex.values.toList()

  /** [Field]s declared in this [ResourceClass]. */
  val declaredFields: List<Field>
    get() = declaredFieldsIndex.values.toList()

  /**
   * A Java field declaration. The [ResourceClass] only supports int and int array fields as they
   * are the only needed by R class parsing.
   */
  sealed class Field {
    abstract val name: String

    /**
     * Represents an integer field.
     *
     * @param name The name of the field.
     * @param value The integer value of the field.
     */
    data class Int(override val name: String, val isStatic: Boolean, val value: kotlin.Int) :
      Field()

    /**
     * Represents an integer field that could not be calculated at load time. This can happen if the
     * value of the field depends on other classes that might not have been loaded at yet. The
     * resource class loading will do a pass once everything is loaded to fix the unresolved
     * references.
     */
    data class UnresolvedInt(
      override val name: String,
      val sourceType: String,
      val sourceFieldName: String,
    ) : Field()

    data class IntArray(override val name: String, val isStatic: Boolean, val value: List<Field>) :
      Field()
  }
}

/**
 * A [MethodVisitor] that "executes" the static initializer so we can resolve the value of int
 * arrays or fields that are not known at compile time.
 *
 * This class looks basically for 3 types of instructions:
 * - GETSTATIC: This indicates that we are trying to load a value from somewhere else. Typically,
 *   this is left as an [Field.UnresolvedInt] value and will be resolved at a later stage.
 * - PUTSTATIC: This indicates one of two things. Either we are trying to set the value of an array
 *   or we are initializing a field that was not initialized at compile time.
 * - IASTORE: This signals that we are putting a new int value in the array.
 */
private class InitializerMethodVisitor(
  val onArrayDeclaration: (Boolean, ResourceClass.Field.IntArray) -> Unit,
  val onFieldDeclaration: (ResourceClass.Field.Int) -> Unit,
) : MethodVisitor(Opcodes.ASM9) {
  private val operandStack: MutableList<ResourceClass.Field> = mutableListOf()
  private val arrayStack: MutableList<ResourceClass.Field> = mutableListOf()
  private var hasUnresolvedFields = false

  override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String?) {
    when (opcode) {
      Opcodes.GETSTATIC -> {
        if (descriptor == Type.INT_TYPE.descriptor) {
          operandStack.add(
            ResourceClass.Field.UnresolvedInt(
              "[${arrayStack.size}]",
              owner.substringAfterLast("$"),
              name,
            )
          )
          hasUnresolvedFields = true
        }
      }

      Opcodes.PUTSTATIC -> {
        if (descriptor == "[I") {
          onArrayDeclaration(
            hasUnresolvedFields,
            ResourceClass.Field.IntArray(name, isStatic = true, arrayStack.toList()),
          )
          arrayStack.clear()
        } else if (descriptor == Type.INT_TYPE.descriptor) {
          onFieldDeclaration(
            (operandStack.removeAt(operandStack.lastIndex) as ResourceClass.Field.Int).copy(name = name)
          )
        }
      }
    }
    super.visitFieldInsn(opcode, owner, name, descriptor)
  }

  override fun visitInsn(opcode: Int) {
    if (opcode == Opcodes.IASTORE) {
      arrayStack.add(operandStack.removeAt(operandStack.lastIndex))
    }
    super.visitInsn(opcode)
  }

  override fun visitLdcInsn(value: Any?) {
    if (value is Int) {
      operandStack.add(ResourceClass.Field.Int("[${arrayStack.size}]", false, value))
    }
    super.visitLdcInsn(value)
  }
}

private class ResourceClassVisitor(
  private val resourceClassResolver: (String) -> ByteArray
) : ClassVisitor(Opcodes.ASM9) {
  private val declaredClassesIndex: MutableMap<String, ResourceClass> = mutableMapOf()
  private val declaredFieldsIndex: MutableMap<String, ResourceClass.Field> = mutableMapOf()
  private var hasUnresolvedFields = false
  private var className: String? = null

  /**
   * Resolve an [ResourceClass.Field.UnresolvedInt] to an [ResourceClass.Field.Int] by using the
   * values in the parsed classes.
   */
  private fun ResourceClass.Field.UnresolvedInt.resolve(): ResourceClass.Field.Int =
    declaredClassesIndex[sourceType]!!.declaredFieldsIndex[sourceFieldName]
      as ResourceClass.Field.Int

  /** Resolve all [ResourceClass.Field.UnresolvedInt] in this [ResourceClass]. */
  private fun ResourceClass.resolveUnresolvedFields(): ResourceClass {
    val resolvedFieldsIndex =
      declaredFieldsIndex.mapValues { (name, field) ->
        when (field) {
          is ResourceClass.Field.Int -> field
          is ResourceClass.Field.UnresolvedInt -> field.resolve()
          is ResourceClass.Field.IntArray -> {
            ResourceClass.Field.IntArray(
              name,
              field.isStatic,
              field.value
                .map {
                  when (it) {
                    is ResourceClass.Field.Int -> it
                    is ResourceClass.Field.UnresolvedInt -> it.resolve()
                    is ResourceClass.Field.IntArray ->
                      throw IllegalStateException("Nested arrays are not supported")
                  }
                }
                .toList(),
            )
          }
        }
      }
    return copy(declaredFieldsIndex = resolvedFieldsIndex, hasUnresolvedFields = false)
  }

  /**
   * Returns the parsed [ResourceClass]. The class returned by this method will have all the
   * unresolved values of the inner classes resolved at this point.
   */
  fun getResourceClass(): ResourceClass =
    ResourceClass(
      className!!,
      declaredClassesIndex
        .mapValues { (_, rclass) ->
          // If a class had unresolved fields, we resolve them at this point.
          if (rclass.hasUnresolvedFields) {
            rclass.resolveUnresolvedFields()
          } else {
            rclass
          }
        },
      declaredFieldsIndex,
      hasUnresolvedFields,
    )

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?,
  ) {
    className = name.substringAfterLast("/").substringAfterLast("$")
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor? {
    if (name == "<clinit>") {
      // "Execute" the static initializer
      return InitializerMethodVisitor(
        onArrayDeclaration = { hasUnresolvedFields, array ->
          this.hasUnresolvedFields = this.hasUnresolvedFields || hasUnresolvedFields
          declaredFieldsIndex[array.name] = array
        },
        onFieldDeclaration = { field -> declaredFieldsIndex[field.name] = field },
      )
    }

    return super.visitMethod(access, name, descriptor, signature, exceptions)
  }

  override fun visitField(
    access: Int,
    name: String,
    descriptor: String?,
    signature: String?,
    value: Any?,
  ): FieldVisitor? {
    if (value is Int) {
      // Easy case, declaration of an Int value
      declaredFieldsIndex[name] =
        ResourceClass.Field.Int(name, isStatic = (access and Opcodes.ACC_STATIC) != 0, value)
    }
    return super.visitField(access, name, descriptor, signature, value)
  }

  override fun visitInnerClass(name: String, outerName: String, innerName: String, access: Int) {
    if (outerName.substringAfterLast("/").substringAfterLast("$") != className) {
      // This is a class not defined within the outer class, ignore
      super.visitInnerClass(name, outerName, innerName, access)
      return
    }
    val fqcn = "$className\$$innerName"
    val bytes = resourceClassResolver(fqcn)
    declaredClassesIndex[innerName] =
      resourceIdClassBinaryParser(
        rClassBytes = bytes,
        resourceClassResolver = {
          throw IllegalStateException(
            "Invalid R class format. Type classes should not contain inner classes. $it"
          )
        },
      )
    super.visitInnerClass(name, outerName, innerName, access)
  }
}

/**
 * Parses an R class from byte code and returns the R class data structure.
 *
 * @param rClassBytes The byte code of the R class.
 * @param resourceClassResolver A function that resolves resource classes by their fully qualified
 *   names.
 * @return The R class data structure.
 * @throws IllegalStateException If the R class format is invalid.
 */
internal fun resourceIdClassBinaryParser(
  rClassBytes: ByteArray,
  resourceClassResolver: (String) -> ByteArray,
): ResourceClass {
  try {
    val classReader = ClassReader(rClassBytes)
    val resourceClassVisitor = ResourceClassVisitor(resourceClassResolver)
    classReader.accept(resourceClassVisitor, 0)
    return resourceClassVisitor.getResourceClass()
  } catch (t: NoSuchElementException) {
    // If the class fails to parser, try to log the contents. This to debug b/401185877
    throw IllegalStateException("Invalid R class format\n${dumpClassToText(rClassBytes)}", t)
  }
}

private fun dumpClassToText(classBytes: ByteArray): String =
  try {
    val classReader = ClassReader(classBytes)
    val stringWriter = StringWriter()
    val traceClassVisitor = TraceClassVisitor(PrintWriter(stringWriter))
    classReader.accept(traceClassVisitor, 0)
    stringWriter.toString()
  } catch (_: Throwable) {
    ""
  }
