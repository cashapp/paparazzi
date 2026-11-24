package app.cash.paparazzi.gradle.reporting

import org.gradle.internal.html.SimpleHtmlWriter
import java.io.IOException

internal class PackagePageRenderer : PageRenderer<PackageTestResults>() {
  @Throws(IOException::class)
  override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) {
    htmlWriter
      .startElement("div")
      .attribute("class", "breadcrumbs")
    htmlWriter
      .startElement("a")
      .attribute("href", results.getUrlTo(results.parent!!))
      .characters("all").endElement()
    htmlWriter.characters(" > " + results.name)
    htmlWriter.endElement()
  }

  @Throws(IOException::class)
  private fun renderClasses(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")

    htmlWriter.startElement("th").characters("Class").endElement()
    htmlWriter.startElement("th").characters("Tests").endElement()
    htmlWriter.startElement("th").characters("Failures").endElement()
    htmlWriter.startElement("th").characters("Ignored").endElement()
    htmlWriter.startElement("th").characters("Duration").endElement()
    htmlWriter.startElement("th").characters("Success rate").endElement()

    htmlWriter.endElement()
    htmlWriter.endElement()

    for (testClass in results.getClasses()) {
      htmlWriter.startElement("tr")
      htmlWriter
        .startElement("td")
        .attribute("class", testClass.statusClass)
      htmlWriter
        .startElement("a")
        .attribute("href", results.getUrlTo(testClass).asHtmlLinkEncoded())
        .characters(testClass.reportName)
        .endElement()
      htmlWriter.endElement()
      htmlWriter
        .startElement("td")
        .characters(testClass.testCount.toString())
        .endElement()
      htmlWriter
        .startElement("td")
        .characters(testClass.failureCount.toString())
        .endElement()
      htmlWriter
        .startElement("td")
        .characters(testClass.ignoredCount.toString())
        .endElement()
      htmlWriter
        .startElement("td")
        .characters(testClass.formattedDuration)
        .endElement()
      htmlWriter
        .startElement("td")
        .attribute("class", testClass.statusClass)
        .characters(testClass.formattedSuccessRate)
        .endElement()
      htmlWriter.endElement()
    }
    htmlWriter.endElement()
  }

  override fun registerTabs() {
    addFailuresTab()
    addIgnoredTab()
    addTab(
      "Classes",
      object : ErroringAction<SimpleHtmlWriter>() {
        @Throws(IOException::class)
        public override fun doExecute(htmlWriter: SimpleHtmlWriter) {
          renderClasses(htmlWriter)
        }
      }
    )
  }
}
