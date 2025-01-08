// Copyright Square, Inc.
package app.cash.paparazzi.preview

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.annotations.PaparazziPreviewData

internal fun Paparazzi.snapshotDefault(
  previewData: PaparazziPreviewData.Default,
  name: String?
) {
  snapshot(name) {
    previewData.composable()
  }
}
