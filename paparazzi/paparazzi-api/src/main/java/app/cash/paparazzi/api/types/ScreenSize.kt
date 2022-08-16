package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object ScreenSize {
  const val DEFAULT = ""

  const val SMALL = "SMALL"
  const val NORMAL = "NORMAL"
  const val LARGE = "LARGE"
  const val XLARGE = "XLARGE"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    ScreenSize.DEFAULT,
    ScreenSize.NORMAL,
    ScreenSize.LARGE,
    ScreenSize.XLARGE,
  ]
)
annotation class DeviceScreenSize
