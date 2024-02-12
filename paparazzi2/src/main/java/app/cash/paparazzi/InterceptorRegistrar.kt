package app.cash.paparazzi

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers

public object InterceptorRegistrar {
  private val byteBuddy = ByteBuddy()
  private val methodInterceptors = mutableListOf<() -> Unit>()

  public fun addMethodInterceptor(
    receiver: Class<*>,
    methodName: String,
    interceptor: Class<*>
  ): Unit = addMethodInterceptors(receiver, setOf(methodName to interceptor))

  public fun addMethodInterceptors(
    receiver: Class<*>,
    methodNamesToInterceptors: Set<Pair<String, Class<*>>>
  ) {
    methodInterceptors += {
      var builder = byteBuddy
        .redefine(receiver)

      methodNamesToInterceptors.forEach {
        builder = builder
          .method(ElementMatchers.named(it.first))
          .intercept(MethodDelegation.to(it.second))
      }

      builder
        .make()
        .load(receiver.classLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
  }

  public fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }

  public fun clearMethodInterceptors() {
    methodInterceptors.clear()
  }
}
