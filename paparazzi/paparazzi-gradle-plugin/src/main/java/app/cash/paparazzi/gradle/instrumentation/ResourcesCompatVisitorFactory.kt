package app.cash.paparazzi.gradle.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

abstract class ResourcesCompatVisitorFactory :
  AsmClassVisitorFactory<ResourcesCompatVisitorFactory.Parameters> {

  interface Parameters : InstrumentationParameters {
    @get:Input
    val platform: Property<Platform>
  }

  private val platform: Platform
    get() = parameters.get().platform.get()

  override fun createClassVisitor(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor
  ): ClassVisitor {
    return ResourcesCompatVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor, platform)
  }

  override fun isInstrumentable(classData: ClassData): Boolean = true

  class ResourcesCompatVisitor(private val apiVersion: Int, nextClassVisitor: ClassVisitor, private val platform: Platform) :
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
        return LoadFontVisitor(apiVersion, mv, platform)
      }
      return mv
    }

    class LoadFontVisitor(apiVersion: Int, nextMethodVisitor: MethodVisitor, private val platform: Platform) :
      MethodVisitor(apiVersion, nextMethodVisitor) {

      private var isStartsWithMethod: Boolean = false

      override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
      ) {
        isStartsWithMethod = "startsWith" == name
        if (isStartsWithMethod) {
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

      override fun visitLdcInsn(value: Any?) {
        if (isStartsWithMethod && value == "res/" && platform == Platform.Windows) {
          super.visitLdcInsn("res\\")
        } else {
          super.visitLdcInsn(value)
        }
      }
    }
  }
}
