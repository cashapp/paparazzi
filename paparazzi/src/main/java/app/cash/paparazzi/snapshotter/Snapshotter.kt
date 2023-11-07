package app.cash.paparazzi.snapshotter

import android.view.View
import androidx.compose.runtime.Composable

interface Snapshotter {
  fun snapshot(
    view: View,
    timestampMillis: Long = 0L
  ): Snapshot

  fun snapshot(
    composable: @Composable () -> Unit,
    timestampMillis: Long = 0L
  ): Snapshot

  fun clip(
    view: View,
    clipSpec: ClipSpec
  ): Clip

  fun clip(
    composable: @Composable () -> Unit,
    clipSpec: ClipSpec
  ): Clip
}
