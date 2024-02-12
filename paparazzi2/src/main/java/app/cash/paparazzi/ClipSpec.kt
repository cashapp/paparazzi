package app.cash.paparazzi

import java.util.concurrent.TimeUnit

public data class ClipSpec(
  val frameSpec: FrameSpec,
  val start: Long,
  val end: Long,
  val fps: Int
) {
  private val durationMillis: Int
    get() = (end - start).toInt()

  // Add one to the frame count so we get the last frame. Otherwise a 1 second, 60 FPS animation
  // our 60th frame will be at time 983 ms, and we want our last frame to be 1,000 ms. This gets
  // us 61 frames for a 1 second animation, 121 frames for a 2 second animation, etc.
  private val frameCount: Int
    get() = (durationMillis * fps) / 1000 + 1

  val frame: Sequence<ClipFrameSpec>
    get() = (0 until frameCount).asSequence().map { frameNumber ->
      val nowMillis = start + (frameNumber * 1000 / fps)
      val snapshotTimeNanos = TimeUnit.MILLISECONDS.toNanos(nowMillis)
      ClipFrameSpec(snapshotTimeNanos)
    }
}

// TODO: This name is shit
public data class ClipFrameSpec(
  val snapshotTimeNanos: Long
)
