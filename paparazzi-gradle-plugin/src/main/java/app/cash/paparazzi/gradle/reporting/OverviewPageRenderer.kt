package app.cash.paparazzi.gradle.reporting

import org.gradle.internal.html.SimpleHtmlWriter
import java.io.IOException

internal class OverviewPageRenderer : PageRenderer<AllTestResults>() {
  override fun registerTabs() {
    addFailuresTab()
    addIgnoredTab()
    if (!results.getPackages().isEmpty()) {
      addTab(
        "Packages",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(writer: SimpleHtmlWriter) {
            renderPackages(writer)
          }
        }
      )
    }
    addTab(
      "Classes",
      object : ErroringAction<SimpleHtmlWriter>() {
        @Throws(IOException::class)
        public override fun doExecute(writer: SimpleHtmlWriter) {
          renderClasses(writer)
        }
      }
    )
  }

  override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) = Unit

  @Throws(IOException::class)
  private fun renderPackages(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")
    htmlWriter.startElement("th").characters("Package").endElement()
    htmlWriter.startElement("th").characters("Tests").endElement()
    htmlWriter.startElement("th").characters("Failures").endElement()
    htmlWriter.startElement("th").characters("Ignored").endElement()
    htmlWriter.startElement("th").characters("Duration").endElement()
    htmlWriter.startElement("th").characters("Success rate").endElement()
    htmlWriter.endElement()
    htmlWriter.endElement()
    htmlWriter.startElement("tbody")
    for (testPackage in results.getPackages()) {
      htmlWriter.startElement("tr")
      htmlWriter.startElement("td").attribute("class", testPackage.statusClass)
      htmlWriter.startElement("a").attribute("href", testPackage.baseUrl)
        .characters(testPackage.name).endElement()
      htmlWriter.endElement()
      htmlWriter.startElement("td").characters(testPackage.testCount.toString()).endElement()
      htmlWriter.startElement("td").characters(testPackage.failureCount.toString())
        .endElement()
      htmlWriter.startElement("td").characters(testPackage.ignoredCount.toString())
        .endElement()
      htmlWriter.startElement("td").characters(testPackage.formattedDuration).endElement()
      htmlWriter.startElement("td").attribute("class", testPackage.statusClass)
        .characters(testPackage.formattedSuccessRate).endElement()
      htmlWriter.endElement()
    }
    htmlWriter.endElement()
    htmlWriter.endElement()
  }

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
    htmlWriter.startElement("tbody")

    for (testPackage in results.getPackages()) {
      for (testClass in testPackage.getClasses()) {
        htmlWriter
          .startElement("tr")
          .startElement("td")
          .attribute("class", testClass.statusClass)
        htmlWriter
          .startElement("a")
          .attribute("href", testClass.baseUrl.asHtmlLinkEncoded())
          .characters(testClass.name).endElement()
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
    }

    htmlWriter.endElement()
    htmlWriter.endElement()
  }
}
