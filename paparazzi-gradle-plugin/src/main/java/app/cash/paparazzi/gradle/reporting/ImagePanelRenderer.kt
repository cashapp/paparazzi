package app.cash.paparazzi.gradle.reporting

import org.gradle.reporting.ReportRenderer
import java.io.File
import java.util.Base64

internal class ImagePanelRenderer : ReportRenderer<List<ScreenshotDiffImage>, SimpleHtmlWriter>() {

  companion object {
    private val ALT_TEXT_PREFIX: String = "Error displaying image at "
  }

  override fun render(image: List<ScreenshotDiffImage>, htmlWriter: SimpleHtmlWriter) {
    // Wrap in a <span>, to work around CSS problem in IE
    htmlWriter.startElement("span")
      .startElement("table").attribute("style", "table-layout: fixed")

    htmlWriter.startElement("tbody")
      .attribute("class", "grid")
      .attribute(
        "style", "width: 100%"
      ) // this class will render a grid like background to better show the diff between png images with and without background
      .startElement("tr")
    renderImages(htmlWriter, image)
    htmlWriter.endElement().endElement().endElement().endElement()
  }

  private fun renderImages(htmlWriter: SimpleHtmlWriter, images: List<ScreenshotDiffImage>?) {
    if (images == null) {
      htmlWriter.startElement("td")
        .attribute("style", "width: 100%")
        .characters("")
        .endElement()
      return
    }

    images.forEach { image ->
      if (File(image.path).exists()) {
        val base64String = Base64.getEncoder().encodeToString(File(image.path).readBytes())
        htmlWriter.startElement("td")
          .attribute("style", "width: 100%; padding: 1em")
          .startElement("img")
          .attribute("src", "data:image/png;base64, $base64String")
          .attribute("style", "max-width: 100%; height: auto;")
          .attribute("alt", image.text())
          .endElement()
          .endElement()
      } else {
        htmlWriter.startElement("td")
          .attribute("style", "width: 100%")
          .characters(image.text())
          .endElement()
      }
    }
  }

  private fun ScreenshotDiffImage.text(): String = "$ALT_TEXT_PREFIX$path"
}
