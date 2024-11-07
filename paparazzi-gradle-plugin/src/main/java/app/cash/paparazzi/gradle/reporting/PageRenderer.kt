package app.cash.paparazzi.gradle.reporting

import org.gradle.api.Action
import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.reporting.ReportRenderer
import org.gradle.reporting.TabbedPageRenderer
import org.gradle.reporting.TabsRenderer
import java.io.IOException
import java.net.URL

internal abstract class PageRenderer<T : CompositeTestResults> : TabbedPageRenderer<T>() {
  protected lateinit var results: T
  private val tabsRenderer = TabsRenderer<T>()

  @Throws(IOException::class)
  protected abstract fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter)

  protected abstract fun registerTabs()

  override fun getStyleUrl(): URL = STYLE_URL

  protected fun addTab(title: String, contentRenderer: Action<SimpleHtmlWriter>) {
    tabsRenderer.add(
      title,
      object : ReportRenderer<T, SimpleHtmlWriter>() {
        override fun render(model: T, writer: SimpleHtmlWriter) {
          contentRenderer.execute(writer)
        }
      }
    )
  }

  @Throws(IOException::class)
  protected fun renderTabs(htmlWriter: SimpleHtmlWriter) {
    tabsRenderer.render(model, htmlWriter)
  }

  protected fun addFailuresTab() {
    if (results.failures.isNotEmpty()) {
      addTab(
        "Failed tests",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          public override fun doExecute(element: SimpleHtmlWriter) {
            renderFailures(element)
          }
        }
      )
    }
  }

  @Throws(IOException::class)
  protected open fun renderFailures(htmlWriter: SimpleHtmlWriter) {
    renderTestResultList(htmlWriter, results.failures)
  }

  @Throws(IOException::class)
  private fun renderTestResultList(htmlWriter: SimpleHtmlWriter, failures: Set<TestResult>) {
    htmlWriter
      .startElement("ul")
      .attribute("class", "linkList")
    for (test in failures) {
      htmlWriter.startElement("li")
      htmlWriter.startElement("a")
        .attribute("href", results.getUrlTo(test.classResults).asHtmlLinkEncoded())
        .characters(test.classResults.reportName)
        .endElement()
      htmlWriter.characters(".")
      val link =
        results.getUrlTo(test.classResults).asHtmlLinkEncoded() + "#" + test.name
      htmlWriter.startElement("a")
        .attribute("href", link)
        .characters(test.displayName).endElement()
      htmlWriter.endElement()
    }
    htmlWriter.endElement()
  }

  protected fun addIgnoredTab() {
    if (results.ignored.isNotEmpty()) {
      addTab(
        "Ignored tests",
        object : ErroringAction<SimpleHtmlWriter>() {
          @Throws(IOException::class)
          public override fun doExecute(htmlWriter: SimpleHtmlWriter) {
            renderIgnoredTests(htmlWriter)
          }
        }
      )
    }
  }

  @Throws(IOException::class)
  protected fun renderIgnoredTests(htmlWriter: SimpleHtmlWriter) {
    renderTestResultList(htmlWriter, results.ignored)
  }

  override fun getTitle(): String = model.title

  override fun getPageTitle(): String = "Test results - " + model.title

  override fun getHeaderRenderer(): ReportRenderer<T, SimpleHtmlWriter> {
    return object : ReportRenderer<T, SimpleHtmlWriter>() {
      @Throws(IOException::class)
      override fun render(model: T, htmlWriter: SimpleHtmlWriter) {
        this@PageRenderer.results = model
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
        htmlWriter.startElement("div").attribute("class", "infoBox")
          .attribute("id", "tests")
        htmlWriter.startElement("div").attribute("class", "counter")
          .characters(results.testCount.toString()).endElement()
        htmlWriter.startElement("p").characters("tests").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter.startElement("div").attribute("class", "infoBox")
          .attribute("id", "failures")
        htmlWriter.startElement("div").attribute("class", "counter")
          .characters(results.failureCount.toString()).endElement()
        htmlWriter.startElement("p").characters("failures").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter.startElement("div").attribute("class", "infoBox")
          .attribute("id", "ignored")
        htmlWriter.startElement("div").attribute("class", "counter")
          .characters(results.ignoredCount.toString()).endElement()
        htmlWriter.startElement("p").characters("ignored").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter.startElement("div").attribute("class", "infoBox")
          .attribute("id", "duration")
        htmlWriter.startElement("div").attribute("class", "counter")
          .characters(results.formattedDuration).endElement()
        htmlWriter.startElement("p").characters("duration").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.startElement("td")
        htmlWriter.startElement("div")
          .attribute("class", "infoBox " + results.statusClass)
          .attribute("id", "successRate")
        htmlWriter.startElement("div").attribute("class", "percent")
          .characters(results.formattedSuccessRate).endElement()
        htmlWriter.startElement("p").characters("successful").endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
        htmlWriter.endElement()
      }
    }
  }

  override fun getContentRenderer(): ReportRenderer<T, SimpleHtmlWriter> =
    object : ReportRenderer<T, SimpleHtmlWriter>() {
      @Throws(IOException::class)
      override fun render(model: T, htmlWriter: SimpleHtmlWriter) {
        this@PageRenderer.results = model
        tabsRenderer.clear()
        registerTabs()
        renderTabs(htmlWriter)
      }
    }

  protected fun String.asHtmlLinkEncoded(): String {
    return replace("#", "%23")
  }

  companion object {
    private val STYLE_URL: URL = PageRenderer::class.java.getResource("style.css")!!
  }
}
