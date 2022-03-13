package app.cash.paparazzi.agent

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.implementation.bind.annotation.SuperCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Callable

class InterceptorRegistrar2Test {
  @Before
  fun setup() {
    ByteBuddyAgent.install()

    InterceptorRegistrar.addMethodFacade(
      MemoryDatabase::class.java,
      "load",
      LoggerInterceptor::class.java
    )

    InterceptorRegistrar.registerMethodInterceptors()
  }

  @Test
  fun test() {
    MemoryDatabase.load("info")

    assertThat(logs).containsExactly("starting", "log", "ending")
  }

  @After
  fun teardown() {
    InterceptorRegistrar.clearMethodInterceptors()
  }

  object SomeDelegateInterceptor {
    @Suppress("unused")
    @JvmStatic
    fun intercept(
      @SuperCall zuper: Callable<Boolean>
    ): Boolean {
      logs += "starting"
      try {
        return zuper.call()
      } finally {
        logs += "ending"
      }
    }
  }

  companion object {
    val logs = mutableListOf<String>()
  }
}