package app.cash.paparazzi.snapshot

import android.view.View
import androidx.compose.runtime.Composable

public interface Snapshotter {
  public fun snapshot(
    view: View,
    timestampNanos: Long = 0L
  ): Snapshot

  public fun snapshot(
    composable: @Composable () -> Unit,
    timestampNanos: Long = 0L
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
