package app.cash.paparazzi.agent

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class ResourcesCompatTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM9, delegate) {
  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<String>?
  ): MethodVisitor {
    val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
    return if (name == LOAD_FONT_METHOD_NAME && descriptor == LOAD_FONT_METHOD_DESCRIPTOR) {
      LoadFontVisitor(methodVisitor)
    } else {
      methodVisitor
    }
  }

  private class LoadFontVisitor(delegate: MethodVisitor) : MethodVisitor(Opcodes.ASM9, delegate) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
      if (
        opcode == Opcodes.INVOKEVIRTUAL &&
        owner == "java/lang/String" &&
        name == "startsWith" &&
        descriptor == "(Ljava/lang/String;)Z"
      ) {
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.POP)
        super.visitInsn(Opcodes.ICONST_1)
      } else {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
      }
    }
  }

  internal companion object {
    const val RESOURCES_COMPAT_CLASS_NAME = "androidx.core.content.res.ResourcesCompat"
    private const val LOAD_FONT_METHOD_NAME = "loadFont"
    private const val LOAD_FONT_METHOD_DESCRIPTOR =
      "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
  }
}
