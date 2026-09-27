package app.cash.paparazzi.plugin.test

import android.widget.ScrollView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

/**
 * A view snapshotted repeatedly, scrolled between passes, to capture a screen taller than the
 * device. The overlay layout must be the same size on every pass.
 */
class AccessibilityScrollingSnapshotTest {
  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun scrollingSnapshots() {
    val scrollView = ScrollView(paparazzi.context).apply {
      addView(
        ComposeView(paparazzi.context).apply {
          setContent {
            Column(Modifier.fillMaxWidth()) {
              repeat(24) { i ->
                Text("Row ${i + 1}", Modifier.fillMaxWidth().height(64.dp))
              }
            }
          }
        }
      )
    }

    paparazzi.snapshot(scrollView, name = "pass0")
    scrollView.scrollTo(0, 600)
    paparazzi.snapshot(scrollView, name = "pass1")
    scrollView.scrollTo(0, 1200)
    paparazzi.snapshot(scrollView, name = "pass2")
  }
}
