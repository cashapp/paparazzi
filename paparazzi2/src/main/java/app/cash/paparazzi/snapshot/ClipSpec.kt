package app.cash.paparazzi.snapshot

import java.util.concurrent.TimeUnit

public data class ClipSpec(
  val frameSpec: FrameSpec,
  val start: Long,
  val frameDelayNanos: Long,
  val frameCount: Int
) {
  public constructor(
    frameSpec: FrameSpec,
    fps: Int,
    start: Long,
    end: Long
  ) : this(
    frameSpec = frameSpec,
    start = start,
    frameDelayNanos = TimeUnit.SECONDS.toNanos(1) / fps,
    // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
    // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
    // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
    frameCount = ((end - start).toInt() * fps) / 1000 + 1
  )

  val frameTimeNanos: Sequence<Long>
    get() = (0 until frameCount).asSequence().map { frameNumber ->
      start + (frameNumber * frameDelayNanos)
    }
}
