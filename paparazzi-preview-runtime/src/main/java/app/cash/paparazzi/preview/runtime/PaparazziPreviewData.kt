package app.cash.paparazzi.preview.runtime

import androidx.compose.runtime.Composable

/**
 * Represents composables annotated with @Paparazzi annotation
 *
 * Default - Represents a composable with no parameters
 */
public sealed interface PaparazziPreviewData {

  public data class Default(
    val snapshotName: String,
    val composable: @Composable () -> Unit
  ) : PaparazziPreviewData {
    override fun toString(): String = snapshotName
  }
}
