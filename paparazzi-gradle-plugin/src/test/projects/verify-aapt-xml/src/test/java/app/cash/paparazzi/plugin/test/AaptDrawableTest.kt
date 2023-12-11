package app.cash.paparazzi.plugin.test

import android.view.View
import android.widget.ImageView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class AaptDrawableTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun aaptEmbeddedDrawable() {
    val view = paparazzi.inflate<View>(R.layout.aapt_drawable)
    val imageView = view.findViewById<ImageView>(R.id.image)
    imageView.setImageResource(R.drawable.card_chip)
    paparazzi.snapshot(view)
  }
}
