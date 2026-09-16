package app.cash.paparazzi.plugin.test

import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.TestName
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

/** Text is nondeterministic so a verify run always differs from what was recorded. */
class JupiterVerifyTest {
  private lateinit var paparazzi: Paparazzi

  @BeforeEach
  fun setup(testInfo: TestInfo) {
    paparazzi = Paparazzi().apply {
      setup(
        testName = TestName(
          packageName = testInfo.testClass.get().`package`?.name.orEmpty(),
          className = testInfo.testClass.get().simpleName,
          methodName = testInfo.testMethod.get().name
        )
      )
    }
  }

  @AfterEach
  fun tearDown() {
    paparazzi.teardown()
  }

  @Test
  fun verify() {
    paparazzi.snapshot(
      TextView(paparazzi.context).apply {
        text = "jupiter ${System.nanoTime()}"
        textSize = 24f
      }
    )
  }
}
