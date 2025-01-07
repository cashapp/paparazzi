package app.cash.paparazzi.annotations

import androidx.compose.runtime.Composable

data class PaparazziPreviewData(
  val snapshotName: String,
  val composable: @Composable () -> Unit
)
