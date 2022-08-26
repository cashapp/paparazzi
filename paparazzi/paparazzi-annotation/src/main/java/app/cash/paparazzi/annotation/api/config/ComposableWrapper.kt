package app.cash.paparazzi.annotation.api.config

import androidx.compose.runtime.Composable


interface ComposableWrapper {
  @Composable
  fun wrap(content: @Composable () -> Unit)
}

abstract class ValuesComposableWrapper<T> : ComposableWrapper {
  final override fun wrap(content: () -> Unit) {}

  abstract val values: Sequence<T>

  @Composable
  abstract fun wrap(value: T, content: @Composable () -> Unit)
}
