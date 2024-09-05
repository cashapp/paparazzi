package app.cash.paparazzi.gradle.reporting

import org.gradle.reporting.ReportRenderer

internal class ImagePanelRenderer : ReportRenderer<List<ScreenshotDiffImage>, SimpleHtmlWriter>() {
  override fun render(image: List<ScreenshotDiffImage>, htmlWriter: SimpleHtmlWriter) {
    // Wrap in a <span>, to work around CSS problem in IE
    htmlWriter.startElement("span")
      .startElement("table").attribute("style", "table-layout: fixed")

    htmlWriter.startElement("tbody")
      .attribute("class", "grid")
      .attribute(
        "style", "width: 100%"
      ) // this class will render a grid like background to better show the diff between png images with and without background
    renderImages(htmlWriter, image)
    htmlWriter.endElement().endElement().endElement()
  }

  private fun renderImages(htmlWriter: SimpleHtmlWriter, images: List<ScreenshotDiffImage>?) {
    if (images == null) {
      htmlWriter
        .startElement("tr")
        .startElement("td")
        .attribute("style", "width: 100%")
        .characters("")
        .endElement()
        .endElement()
      return
    }

    images.forEach { image ->
      if (image.base64EncodedImage.isNotEmpty()) {
        htmlWriter
          .startElement("tr")
          .startElement("td")
          .attribute("style", "width: 100%; padding: 1em")
          .startElement("h4")
          .characters(image.snapshotName)
          .endElement()
          .startElement("img")
          .attribute("src", "data:image/png;base64, ${image.base64EncodedImage}")
          .attribute("style", "max-width: 100%; height: auto;")
          .attribute("alt", image.text())
          .endElement()
          .endElement()
          .endElement()
      } else {
        htmlWriter
          .startElement("tr")
          .startElement("td")
          .attribute("style", "width: 100%")
          .characters(image.text())
          .endElement()
          .endElement()
      }
    }
  }

  private fun ScreenshotDiffImage.text(): String = String.format("Error displaying image at %s", path)
}
