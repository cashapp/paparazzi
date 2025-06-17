package app.cash.paparazzi.gradle.reporting

internal data class DiffImage(
  val path: String, // TODO relative path
  val base64EncodedImage: String
) {
  val text: String
    get() = "Error displaying image for $path"
}
