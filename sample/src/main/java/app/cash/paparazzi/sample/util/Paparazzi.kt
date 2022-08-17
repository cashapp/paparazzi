package app.cash.paparazzi.sample.util

import androidx.compose.runtime.Composable
import app.cash.paparazzi.annotation.api.config.ComposableWrapper
import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.sample.util.DesignTheme.DARK
import app.cash.paparazzi.sample.util.DesignTheme.LIGHT


@Paparazzi(
  name = "Normal",
  fontScale = 1.0f,
)
@Paparazzi(
  name = "Large",
  fontScale = 2.0f,
)
annotation class ScaledPaparazzi


@Paparazzi(
  name = "themed",
  composableWrapper = ThemeComposableWrapper::class
)
annotation class ThemedPaparazzi


class ThemeComposableWrapper: ComposableWrapper<DesignTheme> {
  override val values: List<DesignTheme> = listOf(LIGHT, DARK)

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
