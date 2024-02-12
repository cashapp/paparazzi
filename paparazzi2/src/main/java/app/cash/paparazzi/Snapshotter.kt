package app.cash.paparazzi

import android.view.View
import androidx.compose.runtime.Composable

public interface Snapshotter {
  public fun snapshot(
    view: View,
    timestampMillis: Long = 0L
  ): Snapshot

  public fun snapshot(
    composable: @Composable () -> Unit,
    timestampMillis: Long = 0L
  ): Snapshot

  public fun clip(
    view: View,
    clipSpec: ClipSpec
  ): Clip

  public fun clip(
    composable: @Composable () -> Unit,
    clipSpec: ClipSpec
  ): Clip
}
