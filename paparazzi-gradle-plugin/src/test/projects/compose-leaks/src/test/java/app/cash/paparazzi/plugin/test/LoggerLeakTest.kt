package app.cash.paparazzi.plugin.test

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class LoggerLeakTest {
  private val paparazzi = Paparazzi()
  private val expectExceptionRule = TestRule { base, _ ->
    object : Statement() {
      override fun evaluate() {
        var exception: Exception? = null
        try {
          base.evaluate()
        } catch (e: Exception) {
          exception = e
        }
        assert(exception != null)
      }
    }
  }

  @get:Rule val ignored: RuleChain = RuleChain.outerRule(expectExceptionRule).around(paparazzi)

  @Test
  fun test1() {
    paparazzi.snapshot { ComposeContent() }
  }

  @Test
  fun test2() {
    paparazzi.snapshot { ComposeContent() }
  }

  @Composable
  private fun ComposeContent() {
    throw Exception()
  }
}
