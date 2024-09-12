package app.cash.paparazzi.gradle.reporting

import java.io.IOException

/**
 * Custom OverviewPageRenderer based on Gradle's OverviewPageRenderer
 */
internal class OverviewPageRenderer : PageRenderer<AllTestResults>() {
  override fun registerTabs() {
    addErrorTab()
    addFailuresTab()
    if (!results.getPackages().isEmpty()) {
      addTab(
        "Packages",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(objectToExecute: SimpleHtmlWriter) {
            renderPackages(objectToExecute)
          }
        }
      )
    }
    addTab(
      "Classes",
      object : ErroringAction<SimpleHtmlWriter>() {
        @Throws(IOException::class)
        public override fun doExecute(objectToExecute: SimpleHtmlWriter) {
          renderClasses(objectToExecute)
        }
      }
    )
  }

  override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) {}

  @Throws(IOException::class)
  private fun renderPackages(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")
    htmlWriter.startElement("th").characters("Package").endElement()
    htmlWriter.startElement("th").characters("Tests").endElement()
    htmlWriter.startElement("th").characters("Errors").endElement()
    htmlWriter.startElement("th").characters("Failures").endElement()
    htmlWriter.startElement("th").characters("Skipped").endElement()
    htmlWriter.startElement("th").characters("Duration").endElement()
    htmlWriter.startElement("th").characters("Success rate").endElement()
    htmlWriter.endElement()
    htmlWriter.endElement()
    htmlWriter.startElement("tbody")
    for (testPackage in results.getPackages()) {
      htmlWriter.startElement("tr")
      htmlWriter.startElement("td").attribute("class", testPackage.statusClass)
      htmlWriter.startElement("a")
        .attribute("href", String.format("%s.html", testPackage.getFilename()))
        .characters(testPackage.name)
        .endElement()
      htmlWriter.endElement()
      htmlWriter.startElement("td").characters(testPackage.testCount.toString()).endElement()
      htmlWriter.startElement("td")
        .characters(testPackage.errorCount.toString())
        .endElement()
      htmlWriter.startElement("td")
        .characters(testPackage.failureCount.toString())
        .endElement()
      htmlWriter
        .startElement("td")
        .characters(testPackage.skipCount.toString())
        .endElement()
      htmlWriter.startElement("td")
        .characters(testPackage.getFormattedDuration())
        .endElement()
      htmlWriter.startElement("td")
        .attribute("class", testPackage.statusClass)
        .characters(testPackage.formattedSuccessRate)
        .endElement()
      htmlWriter.endElement()
    }
    htmlWriter.endElement()
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
    htmlWriter.startElement("tbody")
    for (testPackage in results.getPackages()) {
      for (testClass in testPackage.getClasses()) {
        htmlWriter.startElement("tr")
        htmlWriter.startElement("td")
          .attribute("class", testClass!!.statusClass)
          .endElement()
        htmlWriter
          .startElement("a")
          .attribute(
            "href", String.format("%s.html", testClass.getFilename())
          )
          .characters(testClass.name)
          .endElement()
        htmlWriter.startElement("td")
          .characters(testClass.testCount.toString())
          .endElement()
        htmlWriter.startElement("td")
          .characters(testClass.errorCount.toString())
          .endElement()
        htmlWriter.startElement("td")
          .characters(testClass.failureCount.toString())
          .endElement()
        htmlWriter
          .startElement("td")
          .characters(testClass.skipCount.toString())
          .endElement()
        htmlWriter.startElement("td")
          .characters(testClass.getFormattedDuration())
          .endElement()
        htmlWriter.startElement("td").attribute("class", testClass.statusClass).characters(
          testClass.formattedSuccessRate
        ).endElement()
        htmlWriter.endElement()
      }
    }
    htmlWriter.endElement()
    htmlWriter.endElement()
  }
}
