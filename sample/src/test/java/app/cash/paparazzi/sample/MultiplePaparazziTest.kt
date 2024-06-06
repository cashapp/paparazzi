package app.cash.paparazzi.sample

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class MultiplePaparazziTest {

  @get:Rule
  val paparazzi = MultiplePaparazziRule()

  @Test(expected = IllegalStateException::class)
  fun compositeItems() {
    paparazzi.snapshot {
      Text("Text")
    }
  }
}

class MultiplePaparazziRule : TestRule {

  private val first = Paparazzi()

  private val second = Paparazzi()

  override fun apply(base: Statement, description: Description): Statement {
    val firstStatement = first.apply(base, description)
    val secondStatement = second.apply(base, description)
    return object : Statement() {
      override fun evaluate() {
        firstStatement.evaluate()
        secondStatement.evaluate()
      }
    }
  }

  fun snapshot(
    name: String? = null,
    content: @Composable () -> Unit,
  ) {
    first.snapshot("first", content)
    second.snapshot("second", content)
  }
}
