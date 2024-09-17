package app.cash.paparazzi.sample

import androidx.core.content.res.ResourcesCompat
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ResourceCompatTest {

  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun test() {
    assert(ResourcesCompat.getFont(paparazzi.context, R.font.cashmarket_medium) != null)
  }
}
