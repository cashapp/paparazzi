package app.cash.paparazzi.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

/**
 * Allows ResourcesCompat to load fonts from layoutlib's absolute resource paths.
 *
 * Apply the font-path workaround in the test JVM. Registering an AGP ASM visitor with
 * InstrumentationScope.ALL for this single class also transforms and caches every dependency
 * of every test variant, even when those dependencies contain no classes that need patching.
 */
internal object ResourcesCompatPatch {
  private var installed = false

  @Synchronized
  fun install(instrumentation: Instrumentation) {
    if (installed) return

    val classLoader = ResourcesCompatPatch::class.java.classLoader
    val transformer = Transformer(classLoader)
    // Register before scanning, so a concurrent first load cannot miss the patch. AndroidX Core
    // is optional: do not load it, or resolve any of its dependencies, merely to install a patch.
    instrumentation.addTransformer(transformer, true)
    try {
      instrumentation.allLoadedClasses
        .filter { it.name == RESOURCES_COMPAT_CLASS_NAME && it.classLoader === classLoader }
        .forEach { instrumentation.retransformClasses(it) }
      installed = true
    } catch (t: Throwable) {
      instrumentation.removeTransformer(transformer)
      throw t
    }
  }

  private class Transformer(private val targetClassLoader: ClassLoader) : ClassFileTransformer {
    override fun transform(
      loader: ClassLoader?,
      className: String?,
      classBeingRedefined: Class<*>?,
      protectionDomain: ProtectionDomain?,
      classfileBuffer: ByteArray
    ): ByteArray? {
      if (loader !== targetClassLoader || className != RESOURCES_COMPAT_INTERNAL_NAME) return null

      val reader = ClassReader(classfileBuffer)
      // The substitution preserves the stack effect, locals and branches. Keep the existing
      // frames to avoid loading Android classes from inside the class-loading callback.
      val writer = ClassWriter(reader, 0)
      var changed = false
      reader.accept(
        object : ClassVisitor(Opcodes.ASM9, writer) {
          override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<String>?
          ): MethodVisitor {
            val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            return if (name == "loadFont" && descriptor == LOAD_FONT_METHOD_DESCRIPTOR) {
              LoadFontVisitor(visitor) { changed = true }
            } else {
              visitor
            }
          }
        },
        0
      )
      return if (changed) writer.toByteArray() else null
    }
  }

  private class LoadFontVisitor(delegate: MethodVisitor, private val onChanged: () -> Unit) :
    MethodVisitor(Opcodes.ASM9, delegate) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      if (
        opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" &&
        name == "startsWith" && descriptor == "(Ljava/lang/String;)Z"
      ) {
        // Bypass the res/ prefix check, as font paths are absolute on the host.
        // Preserve the stack effect and existing frames; no new branches or locals are needed.
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.ICONST_1)
        onChanged()
      } else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
      }
    }
  }

  private const val RESOURCES_COMPAT_CLASS_NAME = "androidx.core.content.res.ResourcesCompat"
  private const val RESOURCES_COMPAT_INTERNAL_NAME = "androidx/core/content/res/ResourcesCompat"
  private const val LOAD_FONT_METHOD_DESCRIPTOR =
    "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
}
