package app.cash.paparazzi.agent

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers.`is`
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.matcher.ElementMatchers.none
import net.bytebuddy.utility.JavaModule

object InterceptorRegistrar {

  private val methodInterceptors = mutableListOf<() -> Unit>()

  fun addMethodInterceptor(
    receiver: Class<*>,
    methodName: String,
    interceptor: Class<*>
  ) = addMethodInterceptors(receiver, setOf(methodName to interceptor))

  fun addMethodInterceptors(
    receiver: Class<*>,
    methodNamesToInterceptors: Set<Pair<String, Class<*>>>
  ) {
    methodInterceptors += {
      val transformer = object : AgentBuilder.Transformer {
        override fun transform(
          builder: DynamicType.Builder<*>,
          typeDescription: TypeDescription,
          classLoader: ClassLoader?,
          module: JavaModule?,
        ): DynamicType.Builder<*> {
          var builder = ByteBuddy().redefine(receiver)
          methodNamesToInterceptors.forEach {
            builder = builder
              .method(named(it.first))
              .intercept(MethodDelegation.to(it.second))
          }

          return builder
        }
      }

      AgentBuilder.Default()
        .ignore(none())
        .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
        .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
        .with(AgentBuilder.RedefinitionStrategy.REDEFINITION)
        .type(`is`(receiver))
        .transform(transformer)
        .installOnByteBuddyAgent()
    }
  }

  fun registerMethodInterceptors() {
    methodInterceptors.forEach { it.invoke() }
  }

  fun clearMethodInterceptors() {
    methodInterceptors.clear()
  }
}
