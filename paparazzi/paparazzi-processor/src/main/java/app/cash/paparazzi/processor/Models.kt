package app.cash.paparazzi.processor

import app.cash.paparazzi.api.Density
import app.cash.paparazzi.api.DeviceConfig
import app.cash.paparazzi.api.Keyboard
import app.cash.paparazzi.api.KeyboardState
import app.cash.paparazzi.api.Navigation
import app.cash.paparazzi.api.NightMode
import app.cash.paparazzi.api.RenderingMode
import app.cash.paparazzi.api.ScreenOrientation
import app.cash.paparazzi.api.ScreenRatio
import app.cash.paparazzi.api.ScreenSize
import app.cash.paparazzi.api.TouchScreen
import com.google.devtools.ksp.symbol.KSType

data class PaparazziModel(
  val functionName: String,
  val packageName: String,
  val testName: String,
  val composableWrapper: KSType?,
  val environment: EnvironmentModel,
  val device: DeviceModel,
  val theme: String,
  val renderingMode: RenderingMode,
  val appCompatEnabled: Boolean,
  val maxPercentDifference: Double,
  val previewParamTypeName: String?,
  val previewParamProvider: KSType?
)

data class EnvironmentModel(
  val platformDir: String,
  val appTestDir: String,
  val resDir: String,
  val assetsDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val platformDataDir: String,
  val resourcePackageNames: List<String>
)

data class DeviceModel(
  val config: DeviceConfig,
  val screenHeight: Int,
  val screenWidth: Int,
  val xdpi: Int,
  val ydpi: Int,
  val orientation: ScreenOrientation,
  val nightMode: NightMode,
  val density: Density,
  val fontScale: Float,
  val ratio: ScreenRatio,
  val size: ScreenSize,
  val keyboard: Keyboard,
  val touchScreen: TouchScreen,
  val keyboardState: KeyboardState,
  val softButtons: Boolean,
  val navigation: Navigation,
  val released: String
)
