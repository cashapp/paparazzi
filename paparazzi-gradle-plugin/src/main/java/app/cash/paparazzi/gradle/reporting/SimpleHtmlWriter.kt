package app.cash.paparazzi.gradle.reporting

import java.io.IOException
import java.io.Writer

/**
 * Custom SimpleHtmlWriter based on Gradle's SimpleHtmlWriter
 */
internal class SimpleHtmlWriter(
  writer: Writer,
  indent: String? = null
) : SimpleMarkupWriter(writer, indent) {

  init {
    writeHtmlHeader()
  }

  @Throws(IOException::class)
  private fun writeHtmlHeader() {
    writeRaw("<!DOCTYPE html>")
  }

  @Throws(IOException::class)
  override fun startElement(name: String): SimpleMarkupWriter {
    require(isValidHtmlTag(name)) { String.format("Invalid HTML tag: '%s'", name) }
    return super.startElement(name)
  }

  companion object {
    private val VALID_HTML_TAGS: Set<String?> = setOf(
      "html", "head", "meta", "title", "link", "script", "body", "h1", "h2", "h3", "h4", "h5",
      "table", "thead", "tbody", "th", "td", "tr", "ul", "li", "a", "p", "pre", "div", "span",
      "label", "input", "img"
    )

    private fun isValidHtmlTag(name: String): Boolean {
      return VALID_HTML_TAGS.contains(name.lowercase())
    }
  }
}
