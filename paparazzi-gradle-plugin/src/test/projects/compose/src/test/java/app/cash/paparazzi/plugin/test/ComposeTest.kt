package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class ComposeTest {
  val paparazzi = Paparazzi()

  @get:Rule
  val ignored: RuleChain = RuleChain.outerRule(LeakWatcherRule(paparazzi)).around(paparazzi)

  @Test
  fun compose() {
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }
}
