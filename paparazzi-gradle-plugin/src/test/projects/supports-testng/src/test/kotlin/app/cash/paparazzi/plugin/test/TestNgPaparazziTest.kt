package app.cash.paparazzi.plugin.test

import android.view.Gravity
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import org.testng.ITestResult
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class TestNgPaparazziTest {

  lateinit var paparazzi: Paparazzi

  @BeforeMethod
  fun setup(result: ITestResult) {
    val testMethod = result.method
    val className = testMethod.realClass.simpleName
    val methodName = testMethod.methodName
    val packageName = testMethod?.realClass?.`package`?.name.orEmpty()

    val testName = TestName(packageName, className, methodName)

    paparazzi = Paparazzi()
    paparazzi.setup(testName = testName)
  }

  @AfterMethod
  fun tearDown() {
    paparazzi.teardown()
  }

  @Test
  fun `verify testng snapshot`() {
    val textView = paparazzi.inflate<TextView>(android.R.layout.simple_list_item_1).apply {
      text = "Paparazzi TestNg test"
      textSize = 24f
      gravity = Gravity.CENTER
    }

    paparazzi.snapshot(textView)
  }
}
