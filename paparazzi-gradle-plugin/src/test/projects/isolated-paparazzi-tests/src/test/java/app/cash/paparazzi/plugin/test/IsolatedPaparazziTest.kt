package app.cash.paparazzi.plugin.test

import android.view.View
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class IsolatedPaparazziTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun snapshot() {
    paparazzi.snapshot(View(paparazzi.context))
  }
}
