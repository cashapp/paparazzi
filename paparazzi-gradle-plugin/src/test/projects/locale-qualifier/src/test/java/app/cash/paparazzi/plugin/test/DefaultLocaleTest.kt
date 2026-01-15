package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(TestParameterInjector::class)
class DefaultLocaleTest(@TestParameter val locale: Locale) {
  enum class Locale(val tag: String?) {
    DEFAULT(null),
    FR("fr-rFR"),
    GB("en-rGB")
  }

  @get:Rule
  val chain: RuleChain = RuleChain
    .outerRule { base, _ ->
      object : Statement() {
        override fun evaluate() {
          try {
            System.setProperty("app.cash.paparazzi.defaultLocale", locale.tag.orEmpty())
            base.evaluate()
          } catch (_: Exception) {
            System.clearProperty("app.cash.paparazzi.defaultLocale")
          }
        }
      }
    }
    .around(Paparazzi())

  @Test
  fun `verify system property sets default locale`() {
    Paparazzi().apply {
      snapshot(view = inflate(R.layout.title_color))
    }
  }
}
