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
    PIXEL_5;

    fun toNative() = when (this) {
        NEXUS_4 -> app.cash.paparazzi.DeviceConfig.NEXUS_4
        NEXUS_5 -> app.cash.paparazzi.DeviceConfig.NEXUS_5
        NEXUS_7 -> app.cash.paparazzi.DeviceConfig.NEXUS_7
        NEXUS_10 -> app.cash.paparazzi.DeviceConfig.NEXUS_10
        NEXUS_5_LAND -> app.cash.paparazzi.DeviceConfig.NEXUS_5_LAND
        NEXUS_7_2012 -> app.cash.paparazzi.DeviceConfig.NEXUS_7_2012
        PIXEL_C -> app.cash.paparazzi.DeviceConfig.PIXEL_C
        PIXEL -> app.cash.paparazzi.DeviceConfig.PIXEL
        PIXEL_XL -> app.cash.paparazzi.DeviceConfig.PIXEL_XL
        PIXEL_2 -> app.cash.paparazzi.DeviceConfig.PIXEL_2
        PIXEL_2_XL -> app.cash.paparazzi.DeviceConfig.PIXEL_2_XL
        PIXEL_3 -> app.cash.paparazzi.DeviceConfig.PIXEL_3
        PIXEL_3_XL -> app.cash.paparazzi.DeviceConfig.PIXEL_3_XL
        PIXEL_3A -> app.cash.paparazzi.DeviceConfig.PIXEL_3A
        PIXEL_3A_XL -> app.cash.paparazzi.DeviceConfig.PIXEL_3A_XL
        PIXEL_4 -> app.cash.paparazzi.DeviceConfig.PIXEL_4
        PIXEL_4_XL -> app.cash.paparazzi.DeviceConfig.PIXEL_4_XL
        PIXEL_4A -> app.cash.paparazzi.DeviceConfig.PIXEL_4A
        PIXEL_5 -> app.cash.paparazzi.DeviceConfig.PIXEL_5
    }
}

enum class ScreenOrientation {
    PORTRAIT, LANDSCAPE, SQUARE;

    fun toNative() = com.android.resources.ScreenOrientation.valueOf(name)
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
    NODPI;

    fun toNative() = com.android.resources.Density.valueOf(name)
}

enum class ScreenRatio {
    NOTLONG, LONG;

    fun toNative() = com.android.resources.ScreenRatio.valueOf(name)
}

enum class ScreenSize {
    SMALL, NORMAL, LARGE, XLARGE;

    fun toNative() = com.android.resources.ScreenSize.valueOf(name)
}

enum class Keyboard {
    NOKEY, QWERTY, TWELVEKEY;

    fun toNative() = com.android.resources.Keyboard.valueOf(name)
}

enum class TouchScreen {
    NOTOUCH, STYLUS, FINGER;

    fun toNative() = com.android.resources.TouchScreen.valueOf(name)
}

enum class KeyboardState {
    EXPOSED, HIDDEN, SOFT;

    fun toNative() = com.android.resources.KeyboardState.valueOf(name)
}

enum class Navigation {
    NONAV, DPAD, TRACKBALL, WHEEL;

    fun toNative() = com.android.resources.Navigation.valueOf(name)
}

enum class NightMode {
    NOTNIGHT, NIGHT;

    fun toNative() = com.android.resources.NightMode.valueOf(name)
}

enum class RenderingMode {
    NORMAL, V_SCROLL, H_SCROLL, FULL_EXPAND, SHRINK;

    fun toNative() = com.android.ide.common.rendering.api.SessionParams.RenderingMode.valueOf(name)
}
