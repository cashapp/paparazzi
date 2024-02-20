package app.cash.paparazzi.snapshot

import com.android.ide.common.rendering.api.SessionParams.RenderingMode

// This basically includes all of the data required from the SessionParams when setting
// up the RenderSession
public data class FrameSpec(
  val deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  val renderingMode: RenderingMode = RenderingMode.NORMAL,
  val theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  val supportsRtl: Boolean = false,
  val showSystemUi: Boolean = false,
  val appCompatEnabled: Boolean = true
)
