package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.asm.Advice
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

  /**
   * All transformations for a single receiver class. Method delegations and advice for the
   * same class must be applied in one redefine pass; a second redefinition of a class that
   * is built from the original class file would undo the first pass.
   */
  private data class Transformation(
    val receiverClass: String,
    val methodInterceptors: MutableSet<Pair<String, Class<*>>> = mutableSetOf(),
    val advice: MutableList<Pair<String, Class<*>>> = mutableListOf()
  )

  private val transformations = mutableMapOf<String, Transformation>()

  fun addMethodInterceptor(receiverClass: String, methodName: String, interceptor: Class<*>) =
    addMethodInterceptors(receiverClass, setOf(methodName to interceptor))

  fun addMethodInterceptors(receiverClass: String, methodNamesToInterceptors: Set<Pair<String, Class<*>>>) {
    transformations.getOrPut(receiverClass) { Transformation(receiverClass) }.methodInterceptors +=
      methodNamesToInterceptors
  }

  fun addAdvice(receiverClass: String, methodName: String, adviceClass: Class<*>) {
    transformations.getOrPut(receiverClass) { Transformation(receiverClass) }.advice += methodName to adviceClass
  }

  fun registerMethodInterceptors() {
    transformations.values.forEach { transformation ->
      val typeResolution = systemTypePool.describe(transformation.receiverClass)
      if (!typeResolution.isResolved) return@forEach

      var builder = byteBuddy
        .redefine<Any>(typeResolution.resolve(), systemClassFileLocator)

      transformation.methodInterceptors.forEach {
        builder = builder
          .method(ElementMatchers.named(it.first))
          .intercept(MethodDelegation.to(it.second))
      }
      transformation.advice.forEach {
        builder = builder
          .visit(Advice.to(it.second).on(ElementMatchers.named(it.first)))
      }

      builder
        .make()
        .load(systemClassLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
  }

  fun clearMethodInterceptors() {
    transformations.clear()
  }
}
