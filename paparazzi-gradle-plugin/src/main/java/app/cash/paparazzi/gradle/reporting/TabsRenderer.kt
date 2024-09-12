package app.cash.paparazzi.gradle.reporting

import org.gradle.reporting.ReportRenderer
import java.io.IOException

internal class TabsRenderer<T> : ReportRenderer<T, SimpleHtmlWriter>() {
  private val tabs: MutableList<TabDefinition> = ArrayList()

  fun add(title: String, contentRenderer: ReportRenderer<T, SimpleHtmlWriter>) {
    tabs.add(TabDefinition(title, contentRenderer))
  }

  fun clear() {
    tabs.clear()
  }

  @Throws(IOException::class)
  override fun render(model: T, htmlWriterWriter: SimpleHtmlWriter) {
    htmlWriterWriter.startElement("div").attribute("id", "tabs")
    htmlWriterWriter.startElement("ul").attribute("class", "tabLinks")
    for (i in tabs.indices) {
      val tab: TabDefinition = tabs[i]
      val tabId = String.format("tab%s", i)
      htmlWriterWriter.startElement("li")
      htmlWriterWriter.startElement("a")
        .attribute("href", "#$tabId")
        .characters(tab.title)
        .endElement()
      htmlWriterWriter.endElement()
    }
    htmlWriterWriter.endElement()
    for (i in tabs.indices) {
      val tab: TabDefinition = tabs[i]
      val tabId = String.format("tab%s", i)
      htmlWriterWriter.startElement("div").attribute("id", tabId).attribute("class", "tab")
      htmlWriterWriter.startElement("h2").characters(tab.title).endElement()
      tab.renderer.render(model, htmlWriterWriter)
      htmlWriterWriter.endElement()
    }
    htmlWriterWriter.endElement()
  }

  private inner class TabDefinition(
    val title: String,
    val renderer: ReportRenderer<T, SimpleHtmlWriter>
  )
}
