package app.cash.paparazzi.annotation.api.config

import androidx.compose.runtime.Composable

interface ComposableWrapper<T> {
  val values: Sequence<T>

  @Composable
  fun wrap(value: T, content: @Composable () -> Unit)
}

abstract class DefaultComposableWrapper : ComposableWrapper<Unit> {
  override val values = sequenceOf(Unit)
}
