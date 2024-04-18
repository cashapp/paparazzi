package app.cash.paparazzi.annotations

import androidx.compose.runtime.Composable

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
