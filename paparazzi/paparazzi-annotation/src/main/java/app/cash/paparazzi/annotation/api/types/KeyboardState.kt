package app.cash.paparazzi.annotation.api.types

import androidx.annotation.StringDef

object KeyboardState {
  const val DEFAULT = ""

  const val EXPOSED = "EXPOSED"
  const val HIDDEN = "HIDDEN"
  const val SOFT = "SOFT"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    KeyboardState.DEFAULT,
    KeyboardState.EXPOSED,
    KeyboardState.HIDDEN,
    KeyboardState.SOFT,
  ]
)
annotation class DeviceKeyboardState
