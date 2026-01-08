package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.AsmVisitorWrapper
import net.bytebuddy.asm.MemberSubstitution
import net.bytebuddy.description.field.FieldDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.jar.asm.Label
import net.bytebuddy.jar.asm.MethodVisitor
import net.bytebuddy.jar.asm.Opcodes
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool

internal object InterceptorRegistrar {
  private val byteBuddy = ByteBuddy()
  private val systemClassFileLocator = ClassFileLocator.ForClassLoader.ofSystemLoader()
  private val systemTypePool = TypePool.Default.ofSystemLoader()
  private val systemClassLoader = ClassLoader.getSystemClassLoader()

  private val methodInterceptors = mutableListOf<() -> Unit>()

  private const val RESOURCES_COMPAT_CLASS_NAME = "androidx.core.content.res.ResourcesCompat"
  private const val LOAD_FONT_METHOD_NAME = "loadFont"
  private const val LOAD_FONT_METHOD_DESCRIPTOR =
    "(Landroid/content/Context;Landroid/content/res/Resources;Landroid/util/TypedValue;IILandroidx/core/content/res/ResourcesCompat\$FontCallback;Landroid/os/Handler;ZZ)Landroid/graphics/Typeface;"

  fun addMethodInterceptor(receiverClass: String, methodName: String, interceptor: Class<*>) =
    addMethodInterceptors(receiverClass, setOf(methodName to interceptor))

  fun addMethodInterceptors(receiverClass: String, methodNamesToInterceptors: Set<Pair<String, Class<*>>>) {
    val typeResolution = systemTypePool.describe(receiverClass)
    if (!typeResolution.isResolved) return

    methodInterceptors += {
      var builder = byteBuddy
        .redefine<Any>(typeResolution.resolve(), systemClassFileLocator)

      methodNamesToInterceptors.forEach {
        builder = builder
          .method(ElementMatchers.named(it.first))
          .intercept(MethodDelegation.to(it.second))
      }

      builder
        .make()
        .load(systemClassLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
  }

  /**
   * [AsmVisitorWrapper] that fixes a hardcoded path in ResourcesCompat.loadFont.
   * In this method, there is a check that font files have a path that starts with res/.
   * This is not the case in Studio. This replaces the check with one that verifies that the path contains res/.
   *
   * Inspired by ASM fix:
   * https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:rendering/src/com/android/tools/rendering/classloading/ResourcesCompatTransform.kt;drc=5bb41b6d5e519c891a4cd6149234138faa28e1af
   */
  fun registerResourcesCompatFontLoadFix() {
    methodInterceptors += {
      AgentBuilder.Default()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
        .type(ElementMatchers.named<TypeDescription>(RESOURCES_COMPAT_CLASS_NAME))
        .transform { builder, _, _, _, _ ->
          val wrapper: AsmVisitorWrapper =
            AsmVisitorWrapper.ForDeclaredMethods()
              .method(
                ElementMatchers.named<MethodDescription>(LOAD_FONT_METHOD_NAME)
                  .and(ElementMatchers.hasDescriptor<MethodDescription>(LOAD_FONT_METHOD_DESCRIPTOR)),
                { _, _, methodVisitor, _, _, _, _ ->
                  object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                    override fun visitMethodInsn(
                      opcode: Int,
                      owner: String?,
                      name: String?,
                      descriptor: String?,
                      isInterface: Boolean
                    ) {
                      if ("java/lang/String" == owner && "startsWith" == name) {
                        // Skip calls to `startsWith`, return true
                        super.visitInsn(Opcodes.POP) // Pop the arg being passed to `startsWith`, which is "res/"
                        super.visitInsn(Opcodes.POP) // Pop the receiver of `startsWith`, which is the font file path
                        super.visitLdcInsn(1) // Push `1` to the stack, as if `startsWith` returned true
                      } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                      }
                    }
                  }
                }
              )

          @Suppress("UNCHECKED_CAST")
          val typedBuilder = builder as DynamicType.Builder<Any>
          typedBuilder.visit(wrapper)
        }
        .installOn(ByteBuddyAgent.getInstrumentation())
    }
  }

  fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }

  fun clearMethodInterceptors() {
    methodInterceptors.clear()
  }
}
