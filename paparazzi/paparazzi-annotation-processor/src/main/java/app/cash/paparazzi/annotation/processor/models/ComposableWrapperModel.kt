package app.cash.paparazzi.annotation.processor.models

import com.google.devtools.ksp.symbol.KSType

data class ComposableWrapperModel(
  val wrapper: KSType,
  val value: KSType
)
