package app.cash.paparazzi.sample

import android.graphics.Canvas
import android.os.Build
import android.os._Original_Build
import android.view.View
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class BuildTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun build() {
    Paparazzi.javaClass.classLoader.loadClass("android.os.Build")
    paparazzi.snapshot(object: View(paparazzi.context) {
      override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        println(Build.MANUFACTURER)
        println(_Original_Build.MANUFACTURER)
      }
    })
  }
}
