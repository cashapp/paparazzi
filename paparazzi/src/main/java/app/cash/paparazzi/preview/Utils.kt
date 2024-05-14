// Copyright Square, Inc.
package app.cash.paparazzi.preview

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData

internal fun Paparazzi.snapshotDefault(
  previewData: PaparazziPreviewData.Default,
  name: String?,
  wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
  snapshot(name) {
    wrapper { previewData.composable() }
  }
}
