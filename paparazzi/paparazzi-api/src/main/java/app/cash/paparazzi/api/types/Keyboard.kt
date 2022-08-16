package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object Keyboard {
  const val DEFAULT = ""

  const val NOKEY = "NOKEY"
  const val QWERTY = "QWERTY"
  const val TWELVEKEY = "TWELVEKEY"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    Keyboard.DEFAULT,
    Keyboard.NOKEY,
    Keyboard.QWERTY,
    Keyboard.TWELVEKEY,
  ]
)
annotation class DeviceKeyboard
