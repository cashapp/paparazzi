package app.cash.paparazzi.gradle.reporting

import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.reporting.ReportRenderer

internal class ImagePanelRenderer : ReportRenderer<DiffImage, SimpleHtmlWriter>() {
  override fun render(image: DiffImage, htmlWriter: SimpleHtmlWriter) {
    // Wrap in a <span>, to work around CSS problem in IE
    htmlWriter
      .startElement("span")
      .startElement("details")
      .startElement("summary")
      .characters("Show failure diff")
      .endElement()
      .startElement("table")
      .attribute("style", "table-layout: fixed")

    // Render a grid background to better show the diff between images with and without background
    htmlWriter
      .startElement("tbody")
      .attribute("class", "grid")
      .attribute("style", "width: 100%")

    renderImage(image, htmlWriter)

    htmlWriter
      .endElement() // tbody
      .endElement() // table
      .endElement() // details
      .endElement() // span

    htmlWriter
      .startElement("p")
      .characters("")
      .endElement()
  }

  private fun renderImage(image: DiffImage, htmlWriter: SimpleHtmlWriter) {
    htmlWriter
      .startElement("tr")
      .startElement("td")
      .attribute("style", "width: 100%; padding: 1em")
      .startElement("img")
      .attribute("src", "data:image/png;base64, ${image.base64EncodedImage}")
      .attribute("style", "max-width: 100%; height: auto;")
      .attribute("alt", image.text)
      .endElement() // img
      .endElement() // td
      .endElement() // tr
  }
}
