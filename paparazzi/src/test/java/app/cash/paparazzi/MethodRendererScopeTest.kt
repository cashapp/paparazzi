package app.cash.paparazzi

import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Verifier

class MethodRendererScopeTest {
  companion object {
    var beforeCallCount: Int = 0
    var afterCallCount: Int = 0

    @get:ClassRule(order = 1)
    @JvmStatic
    val verifier = object : Verifier() {
      override fun verify() {
        assertThat(beforeCallCount).isEqualTo(5)
        assertThat(afterCallCount).isEqualTo(5)
      }
    }
  }

  @get:Rule(order = 2)
  val rendererScope = object : RendererScope() {
    override fun before() {
      super.before()
      beforeCallCount++
    }

    override fun after() {
      super.after()
      afterCallCount++
    }
  }

  @get:Rule(order = 3)
  val paparazzi = Paparazzi(managedRendererScope = rendererScope)

  @Test fun test1() {}
  @Test fun test2() {}
  @Test fun test3() {}
  @Test fun test4() {}
  @Test fun test5() {}
}