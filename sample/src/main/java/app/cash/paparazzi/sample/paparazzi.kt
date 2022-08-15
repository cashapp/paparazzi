package app.cash.paparazzi.sample

import androidx.compose.runtime.Composable
import app.cash.paparazzi.api.ComposableWrapper
import app.cash.paparazzi.api.Paparazzi

@Paparazzi(
    name = "Light Normal",
    deviceFontScale = 1.0f,
    composableWrapper = LightThemeComposableWrapper::class
)
@Paparazzi(
    name = "Light Large",
    deviceFontScale = 2.0f,
    composableWrapper = LightThemeComposableWrapper::class
)
@Paparazzi(
    name = "Dark Normal",
    deviceFontScale = 1.0f,
    composableWrapper = DarkThemeComposableWrapper::class
)
@Paparazzi(
    name = "Dark Large",
    deviceFontScale = 2.0f,
    composableWrapper = DarkThemeComposableWrapper::class
)
annotation class ThemedScaledPaparazzi


class LightThemeComposableWrapper : ComposableWrapper {
    @Composable
    override fun wrap(content: @Composable () -> Unit) {
        SimpleTheme(DesignTheme.LIGHT) {
            content()
        }
    }
}

class DarkThemeComposableWrapper : ComposableWrapper {
    @Composable
    override fun wrap(content: @Composable () -> Unit) {
        SimpleTheme(DesignTheme.DARK) {
            content()
        }
    }
}
