package app.cash.paparazzi.annotation.processor.models

data class EnvironmentModel(
  val platformDir: String,
  val appTestDir: String,
  val resDir: String,
  val assetsDir: String,
  val packageName: String,
  val compileSdkVersion: Int,
  val platformDataDir: String,
  val resourcePackageNames: List<String>
)
