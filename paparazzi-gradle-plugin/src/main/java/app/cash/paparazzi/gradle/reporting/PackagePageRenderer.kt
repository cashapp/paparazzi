package app.cash.paparazzi.gradle.reporting

import java.io.IOException

/**
 * Custom PackagePageRenderer based on Gradle's PackagePageRenderer
 */
internal class PackagePageRenderer : PageRenderer<PackageTestResults>() {

  @Throws(IOException::class)
  override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("div").attribute("class", "breadcrumbs")
    htmlWriter.startElement("a").attribute("href", "index.html").characters("all").endElement()
    htmlWriter.characters(String.format(" > %s", results.name))
    htmlWriter.endElement()
  }

  @Throws(IOException::class)
  private fun renderClasses(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")
    htmlWriter.startElement("th").characters("Class").endElement()
    htmlWriter.startElement("th").characters("Tests").endElement()
    htmlWriter.startElement("th").characters("Errors").endElement()
    htmlWriter.startElement("th").characters("Failures").endElement()
    htmlWriter.startElement("th").characters("Skipped").endElement()
    htmlWriter.startElement("th").characters("Duration").endElement()
    htmlWriter.startElement("th").characters("Success rate").endElement()
    htmlWriter.endElement()
    htmlWriter.endElement()
    for (testClass in results.getClasses()) {
      htmlWriter.startElement("tr")
      htmlWriter.startElement("td").attribute("class", testClass!!.statusClass)
      htmlWriter.startElement("a")
        .attribute("href", String.format("%s.html", testClass.getFilename()))
        .characters(
          testClass.simpleName
        )
        .endElement()
      htmlWriter.endElement()
      htmlWriter.startElement("td").characters(testClass.testCount.toString()).endElement()
      htmlWriter.startElement("td").characters(testClass.errorCount.toString()).endElement()
      htmlWriter.startElement("td").characters(testClass.failureCount.toString()).endElement()
      htmlWriter
        .startElement("td")
        .characters(testClass.skipCount.toString())
        .endElement()
      htmlWriter.startElement("td").characters(testClass.getFormattedDuration()).endElement()
      htmlWriter.startElement("td").attribute("class", testClass.statusClass).characters(
        testClass.formattedSuccessRate
      ).endElement()
      htmlWriter.endElement()
    }
    htmlWriter.endElement()
  }

  override fun registerTabs() {
    addErrorTab()
    addFailuresTab()
    addTab(
      "Classes",
      object : ErroringAction<SimpleHtmlWriter>() {
        @Throws(IOException::class)
        override fun doExecute(objectToExecute: SimpleHtmlWriter) {
          renderClasses(objectToExecute)
        }
      }
    )
  }
}
