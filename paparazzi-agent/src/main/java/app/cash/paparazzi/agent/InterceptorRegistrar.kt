package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers

object InterceptorRegistrar {
  private val methodInterceptors = mutableListOf<() -> Unit>()

  fun addMethodInterceptor(
    receiver: Class<*>,
    methodName: String,
    interceptor: Class<*>
  ) {
    methodInterceptors += {
      ByteBuddy()
          .rebase(receiver)
          .method(ElementMatchers.named(methodName))
          .intercept(MethodDelegation.to(interceptor))
          .make()
          .load(receiver.classLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
  }

  fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }
}