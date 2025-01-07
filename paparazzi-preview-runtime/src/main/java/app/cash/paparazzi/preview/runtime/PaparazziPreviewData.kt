package app.cash.paparazzi.preview.runtime

import androidx.compose.runtime.Composable

public data class PaparazziPreviewData(
  val snapshotName: String,
  val composable: @Composable () -> Unit
)
