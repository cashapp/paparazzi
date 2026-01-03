package runner

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class PaparazziExtension(
  val api: Paparazzi
) : BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    api.beforeTest(testName = context.toTestName())
  }

  override fun afterEach(context: ExtensionContext) {
    api.afterTest()
  }

  private fun ExtensionContext.toTestName() =
    TestName(
      packageName = this.requiredTestClass.`package`?.name.orEmpty(),
      className = this.requiredTestClass.simpleName,
      methodName = this.requiredTestMethod.name
    )
}
