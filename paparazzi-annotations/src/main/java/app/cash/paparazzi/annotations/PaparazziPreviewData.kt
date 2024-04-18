package app.cash.paparazzi.annotations

import androidx.compose.runtime.Composable

/**
 * Represents composables annotated with @Paparazzi annotation
 *
 * Default - Represents a composable with no parameters
 * Empty - Represents a configuration with zero annotated composables
 * Error - Represents an error state with a message if the composable is misconfgured (ex. private Composable function)
 */
public sealed interface PaparazziPreviewData {

  public data class Default(
    val snapshotName: String,
    val composable: @Composable () -> Unit
  ) : PaparazziPreviewData {
    override fun toString(): String = snapshotName
  }

  public data object Empty : PaparazziPreviewData {
    override fun toString(): String = "Empty"
  }

  public data class Error(
    val snapshotName: String,
    val message: String
  ) : PaparazziPreviewData {
    override fun toString(): String = snapshotName
  }
}
