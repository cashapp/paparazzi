package app.cash.paparazzi.sample.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.annotation.api.Paparazzi
import app.cash.paparazzi.annotation.api.config.ComposableWrapper
import app.cash.paparazzi.annotation.api.config.DefaultComposableWrapper
import app.cash.paparazzi.sample.util.DesignTheme.DARK
import app.cash.paparazzi.sample.util.DesignTheme.LIGHT

@Paparazzi(
  name = "themed",
  composableWrapper = ThemeComposableWrapper::class
)
annotation class ThemedPaparazzi

@Paparazzi(
  name = "themed",
  fontScales = [1.0f, 2.0f],
  composableWrapper = ThemeComposableWrapper::class
)
annotation class ThemedScaledPaparazzi

@Paparazzi(
  name = "green",
  composableWrapper = GreenBoxComposableWrapper::class
)
annotation class GreenPaparazzi

class ThemeComposableWrapper : ComposableWrapper<DesignTheme> {
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

class GreenBoxComposableWrapper : DefaultComposableWrapper() {
  @Composable
  override fun wrap(
    value: Unit,
    content: @Composable () -> Unit
  ) {
    Box(
      modifier = Modifier
        .wrapContentSize()
        .background(Color.Green)
        .padding(24.dp)
    ) {
      content()
    }
  }
}
