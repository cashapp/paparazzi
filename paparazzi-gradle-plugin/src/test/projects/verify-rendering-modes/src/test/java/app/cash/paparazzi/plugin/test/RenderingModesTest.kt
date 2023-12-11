package app.cash.paparazzi.plugin.test

import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Rule
import org.junit.Test

class RenderingModesTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun renderingModes() {
    val linearLayout = LinearLayout(paparazzi.context).apply {
      layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    }

    (0..2).forEach {
      linearLayout.addView(
        TextView(paparazzi.context).apply {
          text = "$it"
          textSize = 128f
          gravity = Gravity.CENTER
          layoutParams = LayoutParams(DeviceConfig.NEXUS_5.screenWidth, DeviceConfig.NEXUS_5.screenHeight)
        }
      )
    }

    paparazzi.snapshot(view = linearLayout) // defaults to NORMAL
    paparazzi.unsafeUpdateConfig(renderingMode = RenderingMode.H_SCROLL)
    paparazzi.snapshot(view = linearLayout)

    paparazzi.unsafeUpdateConfig(renderingMode = RenderingMode.V_SCROLL)
    linearLayout.orientation = LinearLayout.VERTICAL
    paparazzi.snapshot(view = linearLayout)
  }
}
