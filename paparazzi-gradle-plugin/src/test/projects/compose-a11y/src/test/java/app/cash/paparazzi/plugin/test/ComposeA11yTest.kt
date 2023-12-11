package app.cash.paparazzi.plugin.test

import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class ComposeA11yTest {
  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun `mixed compose usage`() {
    val mixedView = MixedView(paparazzi.context)
    paparazzi.snapshot(mixedView)
  }

  @Test
  fun `verify changing view hierarchy order doesn't change accessibility colors`() {
    val mixedView = MixedView(paparazzi.context).apply {
      addView(
        ComposeView(context).apply {
          id = 10
          setContent {
            Box(modifier = Modifier.size(50.dp)) {}
          }
        },
        0,
        LinearLayout.LayoutParams(0, 0)
      )
    }
    paparazzi.snapshot(mixedView)
  }
}
