package app.cash.paparazzi2

// TODO:
// - Figure out what tests and resources also need to move over
// - Figure out what needs to be removed from build.gradle file
// - Refactor RenderExtension set on Device to interceptor pattern
// - Figure out which class to use in Renderer.kt L153
// -

import android.view.View

public class FrameSpec()

public class Device(
  public val frame: FrameSpec

)

public class Snapshot()

public interface Snapshotter {
  public fun snapshot(
    view: View,
    timestamp: Long,
    width: MeasureSpec,
    height: MeasureSpec
  ): Snapshot
}

public sealed class MeasureSpec()

public class FixedSize(size: Int) : MeasureSpec()
public object MatchParent : MeasureSpec()
public object WrapContent : MeasureSpec()

public class LayoutlibSnapshotter(
  public val device: Device,
  public val interceptors: List<SnapshotInterceptor>
) : Snapshotter {
  override fun snapshot(
    view: View,
    timestamp: Long,
    width: MeasureSpec,
    height: MeasureSpec
  ): Snapshot {
    TODO("Not yet implemented")
  }
}

public interface SnapshotInterceptor {
  public fun intercept(
    chain: Chain
  ): Snapshot

  public interface Chain {
    public val view: View
    public val timestamp: Long
    public val widthSpec: MeasureSpec
    public val heightSpec: MeasureSpec

    public fun proceed(
      view: View,
      timestamp: Long,
      width: MeasureSpec,
      height: MeasureSpec
    ): Snapshot
  }
}
