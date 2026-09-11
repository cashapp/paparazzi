package app.cash.paparazzi.plugin.test

import android.widget.LinearLayout
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class CleanRecordParallelTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun record() {
    paparazzi.snapshot(LinearLayout(paparazzi.context))
  }
}
