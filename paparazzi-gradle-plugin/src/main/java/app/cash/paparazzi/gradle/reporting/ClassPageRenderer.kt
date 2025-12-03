package app.cash.paparazzi.gradle.reporting

import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.internal.xml.SimpleMarkupWriter
import org.gradle.reporting.CodePanelRenderer
import java.io.IOException

internal class ClassPageRenderer(
  private val resultsProvider: TestResultsProvider,
  diffRegistryFactory: () -> Map<Pair<String, String>, DiffImage>
) : PageRenderer<ClassTestResults>() {
  private val codePanelRenderer = CodePanelRenderer()
  private val imagePanelRenderer = ImagePanelRenderer()
  private val diffImages: Map<Pair<String, String>, DiffImage> = diffRegistryFactory.invoke()

  @Throws(IOException::class)
  override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) {
    htmlWriter
      .startElement("div")
      .attribute("class", "breadcrumbs")
      .startElement("a")
      .attribute("href", results.getUrlTo(results.parent!!.parent!!))
      .characters("all").endElement()
      .characters(" > ")
      .startElement("a")
      .attribute("href", results.getUrlTo(results.packageResults))
      .characters(results.packageResults.name)
      .endElement()
      .characters(" > " + results.simpleName)
      .endElement()
  }

  @Throws(IOException::class)
  private fun renderTests(htmlWriter: SimpleHtmlWriter) {
    val writer = htmlWriter.startElement("table")
    renderTableHead(writer, determineTableHeaders())

    val methodNameColumnExists = methodNameColumnExists()

    for (test in results.testResults) {
      renderTableRow(writer, test, determineTableRow(test, methodNameColumnExists))
    }
    htmlWriter.endElement()
  }

  private fun determineTableRow(test: TestResult, methodNameColumnExists: Boolean): List<String> =
    if (methodNameColumnExists) {
      listOf(
        test.displayName,
        test.name,
        test.formattedDuration,
        test.formattedResultType
      )
    } else {
      listOf(
        test.displayName,
        test.formattedDuration,
        test.formattedResultType
      )
    }

  private fun determineTableHeaders(): List<String> =
    if (methodNameColumnExists()) {
      listOf("Test", "Method name", "Duration", "Result")
    } else {
      listOf("Test", "Duration", "Result")
    }

  @Throws(IOException::class)
  private fun renderTableHead(writer: SimpleMarkupWriter, headers: List<String>) {
    writer.startElement("thead").startElement("tr")
    for (header in headers) {
      writer.startElement("th").characters(header).endElement()
    }
    writer.endElement().endElement()
  }

  @Throws(IOException::class)
  private fun renderTableRow(writer: SimpleMarkupWriter, test: TestResult, rowCells: List<String>) {
    writer.startElement("tr")
    for (cell in rowCells) {
      writer
        .startElement("td")
        .attribute("class", test.statusClass)
        .characters(cell)
        .endElement()
    }
    writer.endElement()
  }

  private fun methodNameColumnExists(): Boolean = results.testResults.any { it.name != it.displayName }

  @Throws(IOException::class)
  override fun renderFailures(htmlWriter: SimpleHtmlWriter) {
    for (test in results.failures) {
      htmlWriter
        .startElement("div")
        .attribute("class", "test")
        .startElement("a")
        .attribute("name", test.id.toString()).characters("")
        .endElement() // browsers dont understand <a name="..."/>
        .startElement("h3")
        .attribute("class", test.statusClass)
        .characters(test.displayName)
        .endElement()
      for (failure in test.failures) {
        val diffImage = diffImages[results.name to test.name]
        if (diffImage != null) {
          imagePanelRenderer.render(diffImage, htmlWriter)
        }

        val message =
          if (failure.message.isNullOrBlank() && !failure.stackTrace.contains(failure.message)) {
            failure.message + System.lineSeparator() + System.lineSeparator() + failure.stackTrace
          } else {
            failure.stackTrace
          }
        codePanelRenderer.render(
          CodePanelRenderer.Data(message, test.id.toString()),
          htmlWriter
        )
      }
      htmlWriter.endElement()
    }
  }

  override fun registerTabs() {
    addFailuresTab()
    addTab(
      "Tests",
      object : ErroringAction<SimpleHtmlWriter>() {
        @Throws(IOException::class)
        public override fun doExecute(htmlWriter: SimpleHtmlWriter) {
          renderTests(htmlWriter)
        }
      }
    )
    val classId = model.id
    if (resultsProvider.hasOutput(classId, TestOutputEvent.Destination.StdOut)) {
      addTab(
        "Standard output",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(htmlWriter: SimpleHtmlWriter) {
            htmlWriter
              .startElement("span")
              .attribute("class", "code")
              .startElement("pre")
              .characters("")
            resultsProvider.writeAllOutput(classId, TestOutputEvent.Destination.StdOut, htmlWriter)
            htmlWriter.endElement().endElement()
          }
        }
      )
    }
    if (resultsProvider.hasOutput(classId, TestOutputEvent.Destination.StdErr)) {
      addTab(
        "Standard error",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(Exception::class)
          override fun doExecute(htmlWriter: SimpleHtmlWriter) {
            htmlWriter
              .startElement("span")
              .attribute("class", "code")
              .startElement("pre")
              .characters("")
            resultsProvider.writeAllOutput(classId, TestOutputEvent.Destination.StdErr, htmlWriter)
            htmlWriter.endElement().endElement()
          }
        }
      )
    }
  }
}
