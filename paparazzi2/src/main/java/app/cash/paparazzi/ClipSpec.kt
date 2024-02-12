package app.cash.paparazzi

import java.util.concurrent.TimeUnit

public data class ClipSpec(
  val frameSpec: FrameSpec,
  val start: Long,
  val frameDelay: Int,
  val frameCount: Int
) {
  public constructor(
    frameSpec: FrameSpec,
    start: Long,
    end: Long,
    fps: Int
  ) : this(
    frameSpec = frameSpec,
    start = start,
    frameDelay = 1000 / fps,
    // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
    // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
    // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
    frameCount = ((end - start).toInt() * fps) / 1000 + 1
  )

  val frame: Sequence<ClipFrameSpec>
    get() = (0 until frameCount).asSequence().map { frameNumber ->
      val nowMillis = start + (frameNumber * frameDelay)
      val snapshotTimeNanos = TimeUnit.MILLISECONDS.toNanos(nowMillis)
      ClipFrameSpec(snapshotTimeNanos)
    }
}

// TODO: This name is shit
public data class ClipFrameSpec(
  val snapshotTimeNanos: Long
)
