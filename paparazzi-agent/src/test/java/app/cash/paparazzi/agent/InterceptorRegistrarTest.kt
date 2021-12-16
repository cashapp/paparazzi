package app.cash.paparazzi.agent

import net.bytebuddy.agent.ByteBuddyAgent
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class InterceptorRegistrarTest {

  @Test
  fun test() {
    InterceptorRegistrar.addMethodInterceptors(
      Utils::class.java,
      setOf(
        "log1" to Interceptor1::class.java,
        "log2" to Interceptor2::class.java,
      )
    )

    ByteBuddyAgent.install()
    InterceptorRegistrar.registerMethodInterceptors()

    Utils.log1()
    Utils.log2()

    assertThat(logs).containsExactly("intercept1", "intercept2")
  }

  @Test
  fun testOverloadedMethodInterceptor() {
    InterceptorRegistrar.addOverloadedMethodInterceptor(
      ContainsOverloadedMethod::class.java,
      "overloaded",
      listOf(String::class.java),
      OverloadedMethodInterceptor::class.java
    )

    ByteBuddyAgent.install()
    InterceptorRegistrar.registerMethodInterceptors()

    ContainsOverloadedMethod.overloaded()
    ContainsOverloadedMethod.overloaded("ignored")

    assertThat(logs).containsExactly("overloaded1", "interceptOverloaded2")
  }

  @After
  fun teardown() {
    logs.clear()
    InterceptorRegistrar.clearMethodInterceptors()
  }

  object ContainsOverloadedMethod {
    fun overloaded() {
      logs += "overloaded1"
    }

    fun overloaded(@Suppress("UNUSED_PARAMETER") testParam: String) {
      logs += "overloaded2"
    }
  }

  object OverloadedMethodInterceptor {
    @JvmStatic
    fun intercept(@Suppress("UNUSED_PARAMETER") testParam: String) {
      logs += "interceptOverloaded2"
    }
  }

  object Utils {
    fun log1() {
      logs += "original1"
    }
    fun log2() {
      logs += "original2"
    }
  }

  object Interceptor1 {
    @Suppress("unused")
    @JvmStatic
    fun intercept() {
      logs += "intercept1"
    }
  }

  object Interceptor2 {
    @Suppress("unused")
    @JvmStatic
    fun intercept() {
      logs += "intercept2"
    }
  }

  companion object {
    private val logs = mutableListOf<String>()
  }
}