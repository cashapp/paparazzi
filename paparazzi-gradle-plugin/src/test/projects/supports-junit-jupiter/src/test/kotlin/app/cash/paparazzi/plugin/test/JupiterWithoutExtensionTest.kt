package app.cash.paparazzi.plugin.test

import android.view.Gravity
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JupiterWithoutExtensionTest {

  lateinit var paparazzi: Paparazzi

  @BeforeEach
  fun setup(testInfo: TestInfo) {
    val name = TestName(
      packageName = testInfo.testClass.get().`package`?.name.orEmpty(),
      className = testInfo.testClass.get().simpleName,
      methodName = testInfo.testMethod.get().name
    )
    paparazzi = Paparazzi()
    paparazzi.setup(testName = name)
  }

  @AfterEach
  fun tearDown() {
    paparazzi.teardown()
  }

  @ParameterizedTest(name = "Jupiter param test: {0}")
  @ValueSource(strings = ["1", "2"])
  fun `verify parametrized snapshot`(param: String) {
    val textView = paparazzi.inflate<TextView>(android.R.layout.simple_list_item_1)
    textView.apply {
      text = "Jupiter test no extension $param"
      textSize = 24f
      gravity = Gravity.CENTER
    }

    paparazzi.snapshot(view = textView, name = param)
  }
}
