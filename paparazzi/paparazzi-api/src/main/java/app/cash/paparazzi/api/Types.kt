package app.cash.paparazzi.api

enum class DeviceConfig {
  NEXUS_4,
  NEXUS_5,
  NEXUS_7,
  NEXUS_10,
  NEXUS_5_LAND,
  NEXUS_7_2012,
  PIXEL_C,
  PIXEL,
  PIXEL_XL,
  PIXEL_2,
  PIXEL_2_XL,
  PIXEL_3,
  PIXEL_3_XL,
  PIXEL_3A,
  PIXEL_3A_XL,
  PIXEL_4,
  PIXEL_4_XL,
  PIXEL_4A,
  PIXEL_5,
}

enum class ScreenOrientation {
  PORTRAIT, LANDSCAPE, SQUARE
}

enum class Density {
  XXXHIGH,
  DPI_560,
  XXHIGH,
  DPI_440,
  DPI_420,
  DPI_400,
  DPI_360,
  XHIGH,
  DPI_260,
  DPI_280,
  DPI_300,
  DPI_340,
  HIGH,
  DPI_220,
  TV,
  DPI_200,
  DPI_180,
  MEDIUM,
  DPI_140,
  LOW,
  ANYDPI,
  NODPI,
}

enum class ScreenRatio {
  NOTLONG, LONG
}

enum class ScreenSize {
  SMALL, NORMAL, LARGE, XLARGE
}

enum class Keyboard {
  NOKEY, QWERTY, TWELVEKEY
}

enum class TouchScreen {
  NOTOUCH, STYLUS, FINGER
}

enum class KeyboardState {
  EXPOSED, HIDDEN, SOFT
}

enum class Navigation {
  NONAV, DPAD, TRACKBALL, WHEEL
}

enum class NightMode {
  NOTNIGHT, NIGHT
}

enum class RenderingMode {
  NORMAL, V_SCROLL, H_SCROLL, FULL_EXPAND, SHRINK
}
