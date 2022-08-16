package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object ScreenOrientation {
  const val DEFAULT = ""

  const val PORTRAIT = "PORTRAIT"
  const val LANDSCAPE = "LANDSCAPE"
  const val SQUARE = "SQUARE"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    ScreenOrientation.DEFAULT,
    ScreenOrientation.PORTRAIT,
    ScreenOrientation.LANDSCAPE,
    ScreenOrientation.SQUARE,
  ]
)
annotation class DeviceOrientation
