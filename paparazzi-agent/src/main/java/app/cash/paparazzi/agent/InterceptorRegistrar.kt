package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatcher.Junction.Conjunction
import net.bytebuddy.matcher.ElementMatchers

object InterceptorRegistrar {
  private val byteBuddy = ByteBuddy()
  private val methodInterceptors = mutableListOf<() -> Unit>()

  fun addMethodInterceptor(
    receiver: Class<*>,
    methodName: String,
    interceptor: Class<*>
  ) = addMethodInterceptors(receiver, setOf(methodName to interceptor))

  fun addOverloadedMethodInterceptor(
    receiver: Class<*>,
    methodName: String,
    arguments: List<Class<*>>,
    interceptor: Class<*>
  ) {
    methodInterceptors += {
      val methodNameMatcher = ElementMatchers.named<MethodDescription>(methodName)
      val argumentMatchers = arguments.mapIndexed { index, clazz ->
        ElementMatchers.takesArgument<MethodDescription>(index, clazz)
      }
      byteBuddy
        .redefine(receiver)
        .method(Conjunction(argumentMatchers + mutableListOf(methodNameMatcher)))
        .intercept(MethodDelegation.to(interceptor))
        .make()
        .load(receiver.classLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
  }

  fun addMethodInterceptors(
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

  fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }

  fun clearMethodInterceptors() {
    methodInterceptors.clear()
  }
}