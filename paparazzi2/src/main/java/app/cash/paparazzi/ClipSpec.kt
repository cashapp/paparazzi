package app.cash.paparazzi

data class ClipSpec(
  val frameSpec: FrameSpec,
  val start: Long,
  val end: Long,
  val fps: Int
)
