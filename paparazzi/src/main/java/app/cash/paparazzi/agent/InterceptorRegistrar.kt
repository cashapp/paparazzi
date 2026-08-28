package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool

internal object InterceptorRegistrar {
  private val byteBuddy = ByteBuddy()

  /**
   * The loader that defined *this* registrar.
   *
   * Under `PaparazziRunner` that is the sandbox's loader, so the redefinitions land on the
   * sandbox's `android.view.View` rather than the host's. Resolving against the system loader
   * instead — as this did originally — silently instruments the wrong copy: the host's `View` gets
   * the interceptor while the sandbox renders with an untouched one, and `isInEditMode()` starts
   * answering true again.
   *
   * Outside a sandbox this is the application loader, which is the previous behaviour.
   */
  private val targetClassLoader: ClassLoader = InterceptorRegistrar::class.java.classLoader

  private val classFileLocator = ClassFileLocator.ForClassLoader.of(targetClassLoader)
  private val typePool = TypePool.Default.of(targetClassLoader)

  private val methodInterceptors = mutableListOf<() -> Unit>()

  fun addMethodInterceptor(receiverClass: String, methodName: String, interceptor: Class<*>) =
    addMethodInterceptors(receiverClass, setOf(methodName to interceptor))

  fun addMethodInterceptors(receiverClass: String, methodNamesToInterceptors: Set<Pair<String, Class<*>>>) {
    val typeResolution = typePool.describe(receiverClass)
    if (!typeResolution.isResolved) return

    methodInterceptors += {
      var builder = byteBuddy
        .redefine<Any>(typeResolution.resolve(), classFileLocator)

      methodNamesToInterceptors.forEach {
        builder = builder
          .method(ElementMatchers.named(it.first))
          .intercept(MethodDelegation.to(it.second))
      }

      builder
        .make()
        .load(targetClassLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
  }

  fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }

  fun clearMethodInterceptors() {
    methodInterceptors.clear()
  }
}
