package app.cash.paparazzi.annotation.processor.models

import app.cash.paparazzi.annotation.api.config.RenderingMode

data class PaparazziModel(
  val functionName: String,
  val showClassIndex: Boolean,
  val packageName: String,
  val testName: String,
  val environment: EnvironmentModel,
  val device: DeviceModel,
  val theme: String,
  val renderingMode: RenderingMode,
  val appCompatEnabled: Boolean,
  val maxPercentDifference: Double,
  val previewParam: PreviewParamModel?,
  val composableWrapper: ComposableWrapperModel?
)
