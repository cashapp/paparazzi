package app.cash.paparazzi.gradle

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * [ClassVisitor] that fixes a hardcoded path in ResourcesCompat.loadFont.
 * In this method, there is a check that font files have a path that starts with res/.
 * This is not the case in Studio. This replaces the check with one that verifies that the path contains res/.
 */
class ResourcesCompatTransform(delegate: ClassVisitor) : ClassVisitor(Opcodes.ASM7, delegate) {
  private var isResourcesCompatClass: Boolean = false

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<String>?
  ) {
    isResourcesCompatClass = name == RESOURCES_COMPAT_CLASS_NAME
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<String>?
  ): MethodVisitor {
    val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
    if (isResourcesCompatClass && name == LOAD_FONT_METHOD_NAME && descriptor == LOAD_FONT_METHOD_DESCRIPTOR) {
      return LoadFontVisitor(methodVisitor)
    }
    return methodVisitor
  }

  private class LoadFontVisitor(delegate: MethodVisitor) : MethodVisitor(Opcodes.ASM7, delegate) {
    override fun visitMethodInsn(
      opcode: Int,
      owner: String,
      name: String,
      descriptor: String,
      isInterface: Boolean
    ) = if ("startsWith" == name) {
      super.visitMethodInsn(opcode, owner, "contains", "(Ljava/lang/CharSequence;)Z", isInterface)
    } else {
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
  }

  private companion object {
    const val RESOURCES_COMPAT_CLASS_NAME = "androidx/core/content/res/ResourcesCompat"
    const val LOAD_FONT_METHOD_NAME = "loadFont"
    const val LOAD_FONT_METHOD_DESCRIPTOR =
      "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
  }
}
