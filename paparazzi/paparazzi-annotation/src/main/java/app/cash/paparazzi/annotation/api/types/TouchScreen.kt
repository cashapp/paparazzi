package app.cash.paparazzi.annotation.api.types

import androidx.annotation.StringDef

object TouchScreen {
  const val DEFAULT = ""

  const val NOTOUCH = "NOTOUCH"
  const val STYLUS = "STYLUS"
  const val FINGER = "FINGER"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    TouchScreen.DEFAULT,
    TouchScreen.NOTOUCH,
    TouchScreen.STYLUS,
    TouchScreen.FINGER,
  ]
)
annotation class DeviceTouchScreen
