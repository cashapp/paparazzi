package app.cash.paparazzi.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

abstract class ResourceCompatVisitorFactory :
  AsmClassVisitorFactory<InstrumentationParameters.None> {
  override fun createClassVisitor(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor
  ): ClassVisitor {
    return ResourceCompatVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = true

  class ResourceCompatVisitor(private val apiVersion: Int, nextClassVisitor: ClassVisitor) :
    ClassVisitor(apiVersion, nextClassVisitor) {

    private var isResourcesCompatClass: Boolean = false

    override fun visit(
      version: Int,
      access: Int,
      name: String?,
      signature: String?,
      superName: String?,
      interfaces: Array<out String>?
    ) {
      isResourcesCompatClass = name == "androidx/core/content/res/ResourcesCompat"
      super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
      access: Int,
      name: String?,
      descriptor: String?,
      signature: String?,
      exceptions: Array<out String>?
    ): MethodVisitor {
      val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
      if (isResourcesCompatClass && name == "loadFont" &&
        descriptor == "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;II" +
        "Landroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"
      ) {
        return LoadFontVisitor(apiVersion, mv)
      }
      return mv
    }

    class LoadFontVisitor(apiVersion: Int, nextMethodVisitor: MethodVisitor) :
      MethodVisitor(apiVersion, nextMethodVisitor) {

      override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
      ) {
        if ("startsWith" == name) {
          super.visitMethodInsn(
            opcode,
            owner,
            "contains",
            "(Ljava/lang/CharSequence;)Z",
            isInterface
          )
        } else {
          super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
      }
    }
  }
}
