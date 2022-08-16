package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object Navigation {
  const val DEFAULT = ""

  const val NONAV = "NONAV"
  const val DPAD = "DPAD"
  const val TRACKBALL = "TRACKBALL"
  const val WHEEL = "WHEEL"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    Navigation.DEFAULT,
    Navigation.NONAV,
    Navigation.DPAD,
    Navigation.TRACKBALL,
    Navigation.WHEEL,
  ]
)
annotation class DeviceNavigation
