package app.cash.paparazzi.sample

import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ResourceCompatTest {

  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun test() {
    val value = TypedValue()
    paparazzi.context.resources.getValue(R.font.cashmarket_medium, value, true)
    println("file: ${value.string}")

    assert(ResourcesCompat.getFont(paparazzi.context, R.font.cashmarket_medium) != null)
  }
}
