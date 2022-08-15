package app.cash.paparazzi.api

import androidx.compose.runtime.Composable

interface ComposableWrapper {
  @Composable
  fun wrap(content: @Composable () -> Unit)
}
