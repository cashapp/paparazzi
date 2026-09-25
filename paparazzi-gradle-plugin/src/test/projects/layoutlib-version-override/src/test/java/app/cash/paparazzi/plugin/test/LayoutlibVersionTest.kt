package app.cash.paparazzi.plugin.test

import android.animation.ValueAnimator
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Rule
import org.junit.Test

class LayoutlibVersionTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun renders() {
    paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Hello, layoutlib" })
  }

  @Test
  fun rendersShrink() {
    paparazzi.unsafeUpdateConfig(renderingMode = RenderingMode.SHRINK)
    paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Shrink" })
  }

  @Test
  fun rendersGif() {
    val view = TextView(paparazzi.context)
    ValueAnimator.ofFloat(0f, 1f).apply {
      duration = 500L
      addUpdateListener { view.alpha = it.animatedValue as Float }
      start()
    }
    paparazzi.gif(view, end = 500L, fps = 4)
  }
}
