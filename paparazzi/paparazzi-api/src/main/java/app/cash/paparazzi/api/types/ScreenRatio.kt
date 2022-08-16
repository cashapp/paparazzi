package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object ScreenRatio {
  const val DEFAULT = ""

  const val NOTLONG = "NOTLONG"
  const val LONG = "LONG"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    ScreenRatio.DEFAULT,
    ScreenRatio.NOTLONG,
    ScreenRatio.LONG,
  ]
)
annotation class DeviceScreenRatio
