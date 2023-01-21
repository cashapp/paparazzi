package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Paparazzi.Companion.determineHandler
import app.cash.paparazzi.accessibility.A11ySnapshotHandler
import app.cash.paparazzi.accessibility.ComposeA11yExtension
import org.junit.Rule
import org.junit.Test

class ComposeA11yWearTest {
  private val composeA11yExtension = ComposeA11yExtension()
  private val maxPercentDifference = 0.1

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.WEAR_OS_SMALL_ROUND,
    theme = "android:ThemeOverlay.Material.Dark",
    renderExtensions = setOf(composeA11yExtension),
    snapshotHandler = A11ySnapshotHandler(
      delegate = determineHandler(
        maxPercentDifference = maxPercentDifference
      ),
      accessibilityStateFn = { composeA11yExtension.accessibilityState }
    )
  )

  @Test
  fun compose() {
    paparazzi.snapshot {
      VolumeScreen()
    }
  }
}
