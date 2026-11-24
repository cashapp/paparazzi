package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool

internal object InterceptorRegistrar {
  private val byteBuddy = ByteBuddy()
  private val systemClassFileLocator = ClassFileLocator.ForClassLoader.ofSystemLoader()
  private val systemTypePool = TypePool.Default.ofSystemLoader()
  private val systemClassLoader = ClassLoader.getSystemClassLoader()

  private val methodInterceptors = mutableListOf<() -> Unit>()

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

  fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }

  fun clearMethodInterceptors() {
    methodInterceptors.clear()
  }
}
