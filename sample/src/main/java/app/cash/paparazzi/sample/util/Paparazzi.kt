package app.cash.paparazzi.sample.util

import androidx.compose.runtime.Composable
import app.cash.paparazzi.annotation.api.config.ComposableWrapper
import app.cash.paparazzi.annotation.api.Paparazzi

@Paparazzi(
  name = "Light,Normal",
  fontScale = 1.0f,
  composableWrapper = LightThemeComposableWrapper::class,
)
@Paparazzi(
  name = "Light,Large",
  fontScale = 2.0f,
  composableWrapper = LightThemeComposableWrapper::class,
)
@Paparazzi(
  name = "Dark,Normal",
  fontScale = 1.0f,
  composableWrapper = DarkThemeComposableWrapper::class,
)
@Paparazzi(
  name = "Dark,Large",
  fontScale = 2.0f,
  composableWrapper = DarkThemeComposableWrapper::class,
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
