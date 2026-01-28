package app.cash.paparazzi.agent

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class InterceptorRegistrarTest {
  @Before
  fun setup() {
    InterceptorRegistrar.addMethodInterceptors(
      "app.cash.paparazzi.agent.InterceptorRegistrarTest\$Utils",
      setOf(
        "log1" to Interceptor1::class.java,
        "log2" to Interceptor2::class.java
      )
    )
    InterceptorRegistrar.registerInstrumentation()
  }

  @Test
  fun test() {
    Utils.log1()
    Utils.log2()

    assertThat(logs).containsExactly("intercept1", "intercept2").inOrder()
  }

  @After
  fun teardown() {
    InterceptorRegistrar.clearInstrumentation()
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
