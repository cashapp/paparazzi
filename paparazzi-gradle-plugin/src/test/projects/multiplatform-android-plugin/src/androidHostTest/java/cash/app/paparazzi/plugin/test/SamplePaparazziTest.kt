package cash.app.paparazzi.plugin.test

import android.widget.ImageView
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.plugin.test.R
import org.junit.Rule
import org.junit.Test

class SamplePaparazziTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun text() {
    paparazzi.snapshot(
      TextView(paparazzi.context).apply {
        text = "Hello Paparazzi from Android Multiplatform Library!"
        textSize = 18f
      }
    )
  }

  @Test
  fun image() {
    paparazzi.snapshot(
      ImageView(paparazzi.context).apply {
        setImageResource(R.drawable.camera)
      }
    )
  }
}
