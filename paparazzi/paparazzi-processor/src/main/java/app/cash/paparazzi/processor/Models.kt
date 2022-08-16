package app.cash.paparazzi.processor

import com.google.devtools.ksp.symbol.KSType

data class PaparazziModel(
  val functionName: String,
  val packageName: String,
  val testName: String,
  val composableWrapper: KSType?,
  val environment: EnvironmentModel,
  val device: DeviceModel,
  val theme: String,
  val renderingMode: String,
  val appCompatEnabled: Boolean,
  val maxPercentDifference: Double,
  val previewParamTypeName: String?,
  val previewParamProvider: KSType?,
)

data class EnvironmentModel(
  val platformDir: String,
  val appTestDir: String,
  val resDir: String,
  val assetsDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val platformDataDir: String,
  val resourcePackageNames: List<String>,
)

data class DeviceModel(
  val config: String,
  val screenHeight: Int,
  val screenWidth: Int,
  val xdpi: Int,
  val ydpi: Int,
  val orientation: String,
  val nightMode: String,
  val density: String,
  val fontScale: Float,
  val ratio: String,
  val size: String,
  val keyboard: String,
  val touchScreen: String,
  val keyboardState: String,
  val softButtons: Boolean,
  val navigation: String,
  val released: String,
)
