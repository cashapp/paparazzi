package app.cash.paparazzi.accessibility

import android.view.View
import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.SnapshotVerifier
import app.cash.paparazzi.internal.differs.OffByTwo
import org.junit.Rule
import org.junit.Test

class AccessibilityRenderExtensionCustomTouchTargetTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    snapshotHandler = SnapshotVerifier(maxPercentDifference = 0.01, differ = OffByTwo),
    renderExtensions = setOf(AccessibilityRenderExtension(minTouchTargetSizeDp = 20f))
  )

  @Test
  fun `verify custom touch target size is respected`() {
    // NEXUS_5 density is 3.0, so 90px == 30dp and 30px == 10dp. With a 20dp minimum,
    // the 30dp target is fine and the 10dp target is flagged.
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
      addView(
        View(context).apply {
          layoutParams = LinearLayout.LayoutParams(90, 90)
          isClickable = true
          contentDescription = "30dp Clickable Target"
        }
      )
      addView(
        View(context).apply {
          layoutParams = LinearLayout.LayoutParams(30, 30)
          isClickable = true
          contentDescription = "10dp Clickable Target"
        }
      )
    }
    paparazzi.snapshot(view, name = "custom-touch-target-size")
  }
}
