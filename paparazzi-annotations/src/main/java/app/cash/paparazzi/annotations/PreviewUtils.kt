package app.cash.paparazzi.annotations

import app.cash.paparazzi.annotations.FontScale.CUSTOM

internal const val DEFAULT_DEVICE_ID: String = "id:pixel_5"

/**
 * Maps [fontScale] to enum values similar to Preview
 * see:
https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/compose-designer/src/com/android/tools/idea/compose/pickers/preview/enumsupport/PsiEnumValues.kt
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun Float.fontScale() =
  FontScale.entries.find { this == it.value } ?: CUSTOM.apply { value = this@fontScale }

internal enum class FontScale(var value: Float?) {
  DEFAULT(1f), SMALL(0.85f), LARGE(1.15f), LARGEST(1.30f), CUSTOM(null);

  fun displayName() = when (this) {
    CUSTOM -> "fs_$value"
    else -> name
  }
}

internal fun Int.lightDarkName() = when (this and UI_MODE_NIGHT_MASK) {
  UI_MODE_NIGHT_NO -> "Light"
  UI_MODE_NIGHT_YES -> "Dark"
  else -> null
}

internal fun Int.uiModeName() = when (this and UI_MODE_TYPE_MASK) {
  UI_MODE_TYPE_NORMAL -> "Normal"
  UI_MODE_TYPE_DESK -> "Desk"
  UI_MODE_TYPE_CAR -> "Car"
  UI_MODE_TYPE_TELEVISION -> "Television"
  UI_MODE_TYPE_APPLIANCE -> "Appliance"
  UI_MODE_TYPE_WATCH -> "Watch"
  UI_MODE_TYPE_VR_HEADSET -> "VR_Headset"
  else -> null
}

// below values are copied from [android.content.res.Configuration]

internal const val UI_MODE_NIGHT_MASK = 48
internal const val UI_MODE_NIGHT_NO = 16
internal const val UI_MODE_NIGHT_YES = 32

internal const val UI_MODE_TYPE_MASK = 15
internal const val UI_MODE_TYPE_NORMAL = 1
internal const val UI_MODE_TYPE_DESK = 2
internal const val UI_MODE_TYPE_CAR = 3
internal const val UI_MODE_TYPE_TELEVISION = 4
internal const val UI_MODE_TYPE_APPLIANCE = 5
internal const val UI_MODE_TYPE_WATCH = 6
internal const val UI_MODE_TYPE_VR_HEADSET = 7
