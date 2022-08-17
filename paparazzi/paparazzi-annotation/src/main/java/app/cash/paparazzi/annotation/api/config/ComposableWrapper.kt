package app.cash.paparazzi.annotation.api.config

import androidx.compose.runtime.Composable

interface ComposableWrapper<T> {
  val values: List<T>

  @Composable
  fun wrap(value: T, content: @Composable () -> Unit)
}
