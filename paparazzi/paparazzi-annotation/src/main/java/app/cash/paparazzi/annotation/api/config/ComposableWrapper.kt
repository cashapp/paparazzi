package app.cash.paparazzi.annotation.api.config

import androidx.compose.runtime.Composable

interface ComposableWrapper {
  @Composable
  fun wrap(content: @Composable () -> Unit)
}
