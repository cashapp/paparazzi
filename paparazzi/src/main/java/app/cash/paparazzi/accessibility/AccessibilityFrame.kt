package app.cash.paparazzi.accessibility

internal data class AccessibilityFrame(
  val elements: List<AccessibilityElement>,
  val width: Int,
  val height: Int,
  val imageWidth: Int = width,
  val imageHeight: Int = height
)
