package app.cash.paparazzi.gradle.reporting

import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.internal.xml.SimpleMarkupWriter
import org.gradle.reporting.ReportRenderer
import java.io.IOException

/**
 * Copied from [org.gradle.reporting.CodePanelRenderer]
 */
internal class CodePanelRenderer : ReportRenderer<String, SimpleHtmlWriter>() {
  override fun render(text: String, htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("span")
      .attribute("class", "code")
      .startElement("pre")
      .characters(text)
      .endElement();
    addClipboardCopyButton(htmlWriter);
    htmlWriter.endElement();
  }

  /**
   * Copied from [org.gradle.reporting.HtmlWriterTools.addClipboardCopyButton]
   */
  @Throws(IOException::class)
  private fun addClipboardCopyButton(writer: SimpleHtmlWriter): SimpleMarkupWriter {
    return writer
      .startElement("button")
      .attribute("class", "clipboard-copy-btn")
      .attribute("aria-label", "Copy to clipboard")
      .characters("Copy")
      .endElement()
  }
}
