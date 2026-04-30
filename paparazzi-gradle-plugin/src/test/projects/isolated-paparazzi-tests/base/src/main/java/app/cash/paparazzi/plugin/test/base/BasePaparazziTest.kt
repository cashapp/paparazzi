package app.cash.paparazzi.plugin.test.base

import android.view.View
import app.cash.paparazzi.Paparazzi
import org.junit.Rule

abstract class BasePaparazziTest {
  @get:Rule
  val paparazzi = Paparazzi()

  fun snapshotView() {
    paparazzi.snapshot(View(paparazzi.context))
  }
}
