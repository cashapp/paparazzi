package app.cash.paparazzi.sample.util

import androidx.compose.runtime.Composable
import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.annotation.api.config.ValuesComposableWrapper
import app.cash.paparazzi.sample.util.DesignTheme.DARK
import app.cash.paparazzi.sample.util.DesignTheme.LIGHT

@Paparazzi(
  name = "themed,scaled",
  fontScales = [1.0f, 2.0f],
  composableWrapper = ThemeComposableWrapper::class,
)
annotation class MyPaparazzi

class ThemeComposableWrapper : ValuesComposableWrapper<DesignTheme>() {
  override val values = sequenceOf(LIGHT, DARK)

  @Composable
  override fun wrap(
    value: DesignTheme,
    content: @Composable () -> Unit
  ) {
    SimpleTheme(value) {
      content()
    }
  }
}
