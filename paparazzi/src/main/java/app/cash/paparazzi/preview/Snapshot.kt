// Copyright Square, Inc.
package app.cash.paparazzi.preview

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData

/**
 * Take a snapshot of the given [previewData].
 */
public fun Paparazzi.snapshot(
  previewData: PaparazziPreviewData,
  name: String? = null,
  wrapper: @Composable (@Composable () -> Unit) -> Unit = { it() }
) {
  when (previewData) {
    is PaparazziPreviewData.Default -> snapshotDefault(previewData, name, wrapper)
    is PaparazziPreviewData.Empty -> Unit
    is PaparazziPreviewData.Error -> throw Exception(previewData.message)
  }
}
