package app.cash.paparazzi.annotation.processor.models

import app.cash.paparazzi.annotation.api.config.RenderingMode
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
