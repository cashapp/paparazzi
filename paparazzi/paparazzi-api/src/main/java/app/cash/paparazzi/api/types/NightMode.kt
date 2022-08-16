package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object NightMode {
  const val DEFAULT = ""

  const val NOTNIGHT = "NOTNIGHT"
  const val NIGHT = "NIGHT"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    NightMode.DEFAULT,
    NightMode.NOTNIGHT,
    NightMode.NIGHT,
  ]
)
annotation class DeviceNightMode
