package app.cash.paparazzi.snapshotter

data class ClipSpec(
  val frameSpec: FrameSpec,
  val start: Long,
  val end: Long,
  val fps: Int
)
