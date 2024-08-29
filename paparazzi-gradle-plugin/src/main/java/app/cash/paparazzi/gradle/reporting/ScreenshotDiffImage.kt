package app.cash.paparazzi.gradle.reporting

internal data class ScreenshotDiffImage(
  val path: String,
  val testPackage: String,
  val testClass: String,
  val testMethod: String,
  val label: String?
)
