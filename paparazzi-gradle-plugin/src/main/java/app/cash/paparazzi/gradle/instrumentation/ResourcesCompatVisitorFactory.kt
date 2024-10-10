package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal abstract class ResourcesCompatVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
  override fun createClassVisitor(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor
  ): ClassVisitor {
    return ResourcesCompatTransform(nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean {
    return classData.className == RESOURCES_COMPAT_CLASS_NAME
  }

  /**
   * [ClassVisitor] that fixes a hardcoded path in ResourcesCompat.loadFont.
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

      override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
      ) {
        if ("startsWith" == name) {
          super.visitMethodInsn(opcode, owner, "contains", "(Ljava/lang/CharSequence;)Z", isInterface)
        } else {
          super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
      }
    }

    private companion object {
      const val LOAD_FONT_METHOD_NAME = "loadFont"
      const val LOAD_FONT_METHOD_DESCRIPTOR =
        "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
    }
  }

  internal companion object {
    const val RESOURCES_COMPAT_CLASS_NAME = "androidx.core.content.res.ResourcesCompat"
  }
}
