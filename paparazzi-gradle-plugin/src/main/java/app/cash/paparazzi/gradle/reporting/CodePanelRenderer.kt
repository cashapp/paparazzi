package app.cash.paparazzi.gradle.reporting

import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.internal.xml.SimpleMarkupWriter
import org.gradle.reporting.ReportRenderer
import java.io.IOException

/**
 * Copied from [org.gradle.reporting.CodePanelRenderer]
 */
internal class CodePanelRenderer : ReportRenderer<CodePanelRenderer.Data, SimpleHtmlWriter>() {
  override fun render(data: Data, htmlWriter: SimpleHtmlWriter) {
    htmlWriter
      .startElement("span")
      .attribute("class", "code")
      .startElement("pre")
      .attribute("id", data.codePanelId)
      .characters(data.text)
      .endElement()
    addClipboardCopyButton(htmlWriter, data.codePanelId)
    htmlWriter.endElement()
  }

  /**
   * Copied from [org.gradle.reporting.HtmlWriterTools.addClipboardCopyButton]
   */
  @Throws(IOException::class)
  private fun addClipboardCopyButton(writer: SimpleHtmlWriter, copyElementId: String): SimpleMarkupWriter {
    return writer
      .startElement("button")
      .attribute("class", "clipboard-copy-btn")
      .attribute("aria-label", "Copy to clipboard")
      .attribute("data-copy-element-id", copyElementId)
      .characters("Copy").endElement()
  }

  data class Data(val text: String, val codePanelId: String)
}
