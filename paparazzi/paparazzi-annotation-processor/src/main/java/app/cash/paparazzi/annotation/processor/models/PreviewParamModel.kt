package app.cash.paparazzi.annotation.processor.models

import com.google.devtools.ksp.symbol.KSType

data class PreviewParamModel(
  val type: KSType,
  val provider: KSType
)
