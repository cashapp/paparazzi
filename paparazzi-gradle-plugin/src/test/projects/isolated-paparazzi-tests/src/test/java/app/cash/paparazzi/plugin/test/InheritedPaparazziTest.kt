package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.plugin.test.base.BasePaparazziTest
import org.junit.Test

class InheritedPaparazziTest : BasePaparazziTest() {
  @Test
  fun snapshot() {
    snapshotView()
  }
}
