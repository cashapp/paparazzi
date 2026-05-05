package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool

internal object InterceptorRegistrar {
  private val byteBuddy = ByteBuddy()
  private val systemTypePool = TypePool.Default.ofSystemLoader()
  private val systemClassLoader = ClassLoader.getSystemClassLoader()

  private val methodInterceptors = mutableListOf<() -> Unit>()

  fun addMethodInterceptor(receiverClass: String, methodName: String, interceptor: Class<*>) =
    addMethodInterceptors(receiverClass, setOf(methodName to interceptor))

  fun addMethodInterceptors(receiverClass: String, methodNamesToInterceptors: Set<Pair<String, Class<*>>>) {
    val typeResolution = systemTypePool.describe(receiverClass)
    if (!typeResolution.isResolved) return

    methodInterceptors += {
      // Use the bytes of the class as currently loaded in the JVM (via the instrumentation
      // agent installed by ByteBuddyAgent.install()) rather than the original classfile
      // bytes from the system classloader. This prevents "attempted to delete a method"
      // errors when the class was loaded from a version with additional methods (e.g.
      // from a mockable Android jar or after AGP ASM transforms caused it to be loaded
      // early with extra stubs).
      val classFileLocator = ClassFileLocator.ForInstrumentation.fromInstalledAgent(systemClassLoader)
      var builder = byteBuddy
        .redefine<Any>(typeResolution.resolve(), classFileLocator)

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
