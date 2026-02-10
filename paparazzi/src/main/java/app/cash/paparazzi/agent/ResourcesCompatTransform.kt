package app.cash.paparazzi.agent

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * [org.objectweb.asm.ClassVisitor] that fixes a hardcoded path in ResourcesCompat.loadFont.
 * In this method, there is a check that font files have a path that starts with res/.
 * This is not the case in Studio. This replaces the check with one that verifies that the path contains res/.
 *
 * Inspired by ASM fix:
 * https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:rendering/src/com/android/tools/rendering/classloading/ResourcesCompatTransform.kt;drc=5bb41b6d5e519c891a4cd6149234138faa28e1af
 */
internal class ResourcesCompatTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate) {
  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<String>?
  ): MethodVisitor {
    val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
    if (name == LOAD_FONT_METHOD_NAME && descriptor == LOAD_FONT_METHOD_DESCRIPTOR) {
      return LoadFontVisitor(api, methodVisitor)
    }
    return methodVisitor
  }

  private class LoadFontVisitor(api: Int, delegate: MethodVisitor) : MethodVisitor(api, delegate) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      if ("startsWith" == name) {
        // Skip calls to `startsWith`, return true
        super.visitInsn(Opcodes.POP) // Pop the arg being passed to `startsWith`, which is "res/"
        super.visitInsn(Opcodes.POP) // Pop the receiver of `startsWith`, which is the font file path
        super.visitLdcInsn(1) // Push `1` to the stack, as if `startsWith` returned true
      } else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
      }
    }
  }

  companion object {
    const val RESOURCES_COMPAT_CLASS_NAME = "androidx.core.content.res.ResourcesCompat"
    const val LOAD_FONT_METHOD_NAME = "loadFont"
    const val LOAD_FONT_METHOD_DESCRIPTOR =
      "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
  }
}
