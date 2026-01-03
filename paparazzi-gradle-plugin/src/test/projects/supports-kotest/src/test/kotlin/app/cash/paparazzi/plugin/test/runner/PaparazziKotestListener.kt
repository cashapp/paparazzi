package app.cash.paparazzi.plugin.test.runner

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult

class PaparazziKotestListener(
  val api: Paparazzi
) : TestListener {

  override suspend fun beforeTest(testCase: TestCase) {
    api.beforeTest(testCase.toTestName())
    super.beforeTest(testCase)
  }

  override suspend fun afterTest(testCase: TestCase, result: TestResult) {
    super.afterTest(testCase, result)
    api.afterTest()
  }

  private fun TestCase.toTestName() =
    TestName(
      packageName = this.spec::class.java.`package`?.name.orEmpty(),
      className = this::class.simpleName.orEmpty(),
      methodName = this.name.testName
    )
}
