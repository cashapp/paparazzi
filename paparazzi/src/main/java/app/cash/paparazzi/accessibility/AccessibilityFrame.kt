package app.cash.paparazzi.accessibility

internal data class AccessibilityFrame(
  val elements: List<AccessibilityElement>,
  val width: Int,
  val height: Int
)
