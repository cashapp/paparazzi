package app.cash.paparazzi.gradle.reporting

import com.android.build.gradle.internal.test.report.ErroringAction
import java.io.IOException

internal class ScreenshotClassPageRenderer : PageRenderer<ClassTestResults>() {
  private val imagePanelRenderer = ImagePanelRenderer()

  override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("div")
      .attribute("class", "breadcrumbs")
      .startElement("a")
      .attribute("href", "index.html")
      .characters("all")
      .endElement()
      .characters(" > ")
      .startElement("a")
      .attribute(
        "href",
        String.format("%s.html", results.getPackageResults().getFilename())
      )
      .characters(results.getPackageResults().name)
      .endElement()
      .characters(String.format(" > %s", results.simpleName))
      .endElement()
  }

  override fun renderFailures(htmlWriter: SimpleHtmlWriter) {
    for (test in results.failures) {
      val testName = test.name
      htmlWriter.startElement("div")
        .attribute("class", "test")
        .startElement("a")
        .attribute("name", test.id.toString())
        .characters("")
        .endElement() // browsers don't understand <a name="..."/>
        .startElement("h3")
        .attribute("class", test.statusClass)
        .characters(testName)
        .endElement()

      test.screenshotDiffImages?.let {
        imagePanelRenderer.render(it, htmlWriter)
      }

      test.failures.forEach { failure ->
        htmlWriter
          .startElement("span")
          .attribute("class", "code")
          .startElement("pre")
          .characters(failure.stackTrace.orEmpty())
          .endElement()
          .endElement()
      }
      htmlWriter.endElement()
    }
  }

  override fun renderErrors(htmlWriter: SimpleHtmlWriter) {
    for (test in results.errors) {
      val testName = test.name
      htmlWriter.startElement("div")
        .attribute("class", "test")
        .startElement("a")
        .attribute("name", test.id.toString())
        .characters("")
        .endElement() // browsers don't understand <a name="..."/>
        .startElement("h3")
        .attribute("class", test.statusClass)
        .characters(testName)
        .endElement()

      test.screenshotDiffImages?.let {
        imagePanelRenderer.render(it, htmlWriter)
      }
      test.errors.forEach { failure ->
        htmlWriter
          .startElement("span")
          .attribute("class", "code")
          .startElement("pre")
          .characters(failure.message)
          .endElement()
          .endElement()
      }

      htmlWriter.endElement()
    }
  }

  fun renderTests(htmlWriter: SimpleHtmlWriter?) {
    for (test in results.results) {
      htmlWriter!!.startElement("div")
        .attribute("class", "test")
        .startElement("a")
        .attribute("name", test.id.toString())
        .characters("")
        .endElement() // browsers don't understand <a name="..."/>
        .startElement("h3")
        .attribute("class", test.statusClass)
        .characters(test.name)
        .endElement()

      test.errors.forEach { failure ->
        htmlWriter
          .startElement("span")
          .attribute("class", "code")
          .startElement("pre")
          .characters(failure.message)
          .endElement()
          .endElement()
      }

      test.screenshotDiffImages?.let {
        imagePanelRenderer.render(it, htmlWriter)
      }

      test.failures.forEach { failure ->
        htmlWriter
          .startElement("span")
          .attribute("class", "code")
          .startElement("pre")
          .characters(failure.stackTrace.orEmpty())
          .endElement()
          .endElement()
      }

      htmlWriter.endElement()
    }
  }

  override fun registerTabs() {
    addErrorTab()
    addFailuresTab()
    addTab(
      "Tests",
      object : ErroringAction<SimpleHtmlWriter>() {
        @Throws(IOException::class)
        override fun doExecute(objectToExecute: SimpleHtmlWriter) {
          renderTests(objectToExecute)
        }
      }
    )
    addStandardOutputTab()
    addStandardErrorTab()
  }
}
