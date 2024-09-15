package app.cash.paparazzi.gradle.reporting

import org.gradle.api.Action
import org.gradle.reporting.ReportRenderer
import java.io.IOException

/**
 * Custom PageRenderer based on Gradle's PageRenderer
 */
internal abstract class PageRenderer<T : CompositeTestResults> : TabbedPageRenderer<T>() {

  protected lateinit var results: T
  private val tabsRenderer: TabsRenderer<T> = TabsRenderer()

  protected abstract fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter)
  protected abstract fun registerTabs()
  protected fun addTab(
    title: String,
    contentRenderer: Action<SimpleHtmlWriter>
  ) {
    tabsRenderer.add(
      title,
      object : ReportRenderer<T, SimpleHtmlWriter>() {
        override fun render(
          model: T,
          writer: SimpleHtmlWriter
        ) {
          contentRenderer.execute(writer)
        }
      }
    )
  }

  protected fun renderTabs(htmlWriter: SimpleHtmlWriter?) {
    tabsRenderer.render(getModel()!!, htmlWriter!!)
  }

  protected fun addFailuresTab() {
    if (results.failures.isNotEmpty()) {
      addTab(
        "Failed tests",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(objectToExecute: SimpleHtmlWriter) {
            renderFailures(objectToExecute)
          }
        }
      )
    }
  }

  protected fun addStandardOutputTab() {
    if (results.standardOutput.any { it.isNotEmpty() }) {
      addTab(
        "Standard output",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(objectToExecute: SimpleHtmlWriter) {
            renderStandardOutput(objectToExecute)
          }
        }
      )
    }
  }

  protected fun addStandardErrorTab() {
    if (results.standardError.any { it.isNotEmpty() }) {
      addTab(
        "Standard error",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(objectToExecute: SimpleHtmlWriter) {
            renderStandardError(objectToExecute)
          }
        }
      )
    }
  }

  protected fun addErrorTab() {
    if (results.errors.isNotEmpty()) {
      addTab(
        "Errors",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          override fun doExecute(objectToExecute: SimpleHtmlWriter) {
            renderErrors(objectToExecute)
          }
        }
      )
    }
  }

  protected open fun renderErrors(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("ul").attribute("class", "linkList")
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")
    htmlWriter.startElement("th").characters("Class").endElement()
    htmlWriter.startElement("th").characters("Test").endElement()
    htmlWriter.endElement() // tr
    htmlWriter.endElement() // thead
    for (test: TestResult in results.errors) {
      htmlWriter.startElement("tr")
      htmlWriter.startElement("td")
        .attribute("class", test.statusClass)
        .startElement("a")
        .attribute(
          "href",
          String.format("%s.html?title=Errors", test.classResults.getFilename())
        )
        .characters(test.classResults.simpleName)
        .endElement()
        .endElement()
      htmlWriter.startElement("td")
        .attribute("class", test.statusClass)
        .startElement("a")
        .attribute(
          "href",
          String.format(
            "%s.html#%s?title=Errors",
            test.classResults.getFilename(),
            test.name
          )
        )
        .characters(test.name)
        .endElement()
        .endElement()
      htmlWriter.endElement() // tr
    }
    htmlWriter.endElement() // table
    htmlWriter.endElement() // ul
  }

  @Throws(IOException::class)
  protected open fun renderFailures(htmlWriter: SimpleHtmlWriter) {
    val selectFailureTab = "?title=Failed%20tests"
    htmlWriter.startElement("ul").attribute("class", "linkList")
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")
    htmlWriter.startElement("th").characters("Class").endElement()
    htmlWriter.startElement("th").characters("Test").endElement()
    htmlWriter.endElement() // tr
    htmlWriter.endElement() // thead
    for (test: TestResult in results.failures) {
      htmlWriter.startElement("tr")
      htmlWriter.startElement("td")
        .attribute("class", test.statusClass)
        .startElement("a")
        .attribute(
          "href",
          String.format("%s.html%s", test.classResults.getFilename(), selectFailureTab)
        )
        .characters(test.classResults.simpleName)
        .endElement()
        .endElement()
      htmlWriter.startElement("td")
        .attribute("class", test.statusClass)
        .startElement("a")
        .attribute(
          "href",
          String.format(
            "%s.html%s#%s",
            test.classResults.getFilename(),
            selectFailureTab,
            test.name
          )
        )
        .characters(test.name)
        .endElement()
        .endElement()

      htmlWriter.endElement() // tr
    }
    htmlWriter.endElement() // table
    htmlWriter.endElement() // ul
  }

  @Throws(IOException::class)
  protected open fun renderStandardOutput(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("ul").attribute("class", "linkList")

    results.standardOutput.forEach {
      htmlWriter
        .startElement("span")
        .attribute("class", "code")
        .startElement("pre")
        .characters(it)
        .endElement()
        .endElement()
    }

    htmlWriter.endElement() // ul
  }

  @Throws(IOException::class)
  protected open fun renderStandardError(htmlWriter: SimpleHtmlWriter) {
    htmlWriter.startElement("ul").attribute("class", "linkList")

    results.standardError.forEach {
      htmlWriter
        .startElement("span")
        .attribute("class", "code")
        .startElement("pre")
        .characters(it)
        .endElement()
        .endElement()
    }

    htmlWriter.endElement() // ul
  }

  @Throws(IOException::class)
  protected fun renderCompositeResults(
    htmlWriter: SimpleHtmlWriter,
    map: Map<String, CompositeTestResults>,
    name: String
  ) {
    htmlWriter.startElement("table")
    htmlWriter.startElement("thead")
    htmlWriter.startElement("tr")
    htmlWriter.startElement("th").characters(name.toCharArray()).endElement()
    htmlWriter.startElement("th").characters("Tests").endElement()
    htmlWriter.startElement("th").characters("Errors").endElement()
    htmlWriter.startElement("th").characters("Failures").endElement()
    htmlWriter.startElement("th").characters("Skipped").endElement()
    htmlWriter.startElement("th").characters("Duration").endElement()
    htmlWriter.startElement("th").characters("Success rate").endElement()
    htmlWriter.endElement() // tr
    htmlWriter.endElement() // thead
    for (results: CompositeTestResults in map.values) {
      htmlWriter.startElement("tr")
      results.name?.let {
        htmlWriter.startElement("td")
          .attribute("class", results.statusClass)
          .characters(it.toCharArray())
          .endElement()
      }
      htmlWriter.startElement("td").characters(results.testCount.toString()).endElement()
      htmlWriter.startElement("td").characters(results.errorCount.toString()).endElement()
      htmlWriter.startElement("td")
        .characters(results.failureCount.toString())
        .endElement()
      htmlWriter
        .startElement("td")
        .characters(results.skipCount.toString())
        .endElement()
      htmlWriter.startElement("td").characters(results.getFormattedDuration()).endElement()
      htmlWriter.startElement("td").characters(results.formattedSuccessRate).endElement()
      htmlWriter.endElement() // tr
    }
    htmlWriter.endElement() // table
  }

  override fun getTitle(): String {
    return getModel()!!.title
  }

  override val headerRenderer: ReportRenderer<T, SimpleHtmlWriter>
    get() = object : ReportRenderer<T, SimpleHtmlWriter>() {
      @Throws(IOException::class)
      override fun render(
        model: T,
        htmlWriter: SimpleHtmlWriter
      ) {
        results = model
        renderBreadcrumbs(htmlWriter)

        // summary
        htmlWriter.startElement("div").attribute("id", "summary")
        htmlWriter.startElement("table")
        htmlWriter.startElement("tr")
        htmlWriter.startElement("td")
        htmlWriter.startElement("div").attribute("class", "summaryGroup")
        htmlWriter.startElement("table")
        htmlWriter.startElement("tr")
        htmlWriter.startElement("td")
        htmlWriter
          .startElement("div")
          .attribute("class", "infoBox")
          .attribute("id", "tests")
        htmlWriter
          .startElement("div")
          .attribute("class", "counter")
          .characters(results.testCount.toString())
          .endElement()
        htmlWriter.startElement("p").characters("tests").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter
          .startElement("div")
          .attribute("class", "infoBox")
          .attribute("id", "errors")
        htmlWriter
          .startElement("div")
          .attribute("class", "counter")
          .characters(results.errorCount.toString())
          .endElement()
        htmlWriter.startElement("p").characters("errors").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter
          .startElement("div")
          .attribute("class", "infoBox")
          .attribute("id", "failures")
        htmlWriter
          .startElement("div")
          .attribute("class", "counter")
          .characters(results.failureCount.toString())
          .endElement()
        htmlWriter.startElement("p").characters("failures").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter
          .startElement("div")
          .attribute("class", "infoBox")
          .attribute("id", "skipped")
        htmlWriter
          .startElement("div")
          .attribute("class", "counter")
          .characters(results.skipCount.toString())
          .endElement()
        htmlWriter.startElement("p").characters("skipped").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter
          .startElement("div")
          .attribute("class", "infoBox")
          .attribute("id", "duration")
        htmlWriter
          .startElement("div")
          .attribute("class", "counter")
          .characters(results.getFormattedDuration())
          .endElement()
        htmlWriter.startElement("p").characters("duration").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter
          .startElement("div")
          .attribute("class", String.format("infoBox %s", results.statusClass))
          .attribute("id", "successRate")
        htmlWriter
          .startElement("div")
          .attribute("class", "percent")
          .characters(results.formattedSuccessRate)
          .endElement()
        htmlWriter
          .startElement("p")
          .characters(if (results.successRate != null) "successful" else "N/A")
          .endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
      }
    }

  override val contentRenderer: ReportRenderer<T, SimpleHtmlWriter>
    get() {
      return object : ReportRenderer<T, SimpleHtmlWriter>() {
        override fun render(
          model: T,
          htmlWriter: SimpleHtmlWriter
        ) {
          results = model
          tabsRenderer.clear()
          registerTabs()
          renderTabs(htmlWriter)
        }
      }
    }
}
