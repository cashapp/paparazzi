package app.cash.paparazzi.gradle.reporting

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.internal.tasks.testing.report.TestReporter
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.html.SimpleHtmlWriter
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.reporting.HtmlReportBuilder
import org.gradle.reporting.HtmlReportRenderer
import org.gradle.reporting.ReportRenderer
import java.io.File
import java.io.IOException
import kotlin.time.measureTime

internal class PaparazziTestReporter(
  private val buildOperationRunner: BuildOperationRunner,
  private val buildOperationExecutor: BuildOperationExecutor,
  private val diffRegistryFactory: () -> Map<Pair<String, String>, DiffImage>
) : TestReporter {
  init {
    // Rather than copy SimpleHtmlWriter, let's append our desired tags to the allowlist
    val declaredField = SimpleHtmlWriter::class.java.getFieldReflectively("VALID_HTML_TAGS")
    @Suppress("UNCHECKED_CAST")
    declaredField.setStaticValue(declaredField.get(null) as Set<String> + setOf("img", "details", "summary"))
  }

  override fun generateReport(testResultsProvider: TestResultsProvider, reportDir: File) {
    LOG.info("Generating HTML test report...")
    val elapsed = measureTime {
      val model = loadModelFromProvider(testResultsProvider)
      generateFiles(model, testResultsProvider, reportDir)
    }
    LOG.info("Finished generating test html results ({}) into: {}", elapsed, reportDir)
  }

  private fun loadModelFromProvider(resultsProvider: TestResultsProvider): AllTestResults {
    val model = AllTestResults()
    resultsProvider.visitClasses { classResult ->
      model.addTestClass(classResult.id, classResult.className, classResult.classDisplayName)
      val collectedResults = classResult.results
      for (collectedResult in collectedResults) {
        val testResult = model.addTest(
          classResult.id,
          classResult.className,
          classResult.classDisplayName,
          collectedResult.name,
          collectedResult.displayName,
          collectedResult.duration
        )
        if (collectedResult.resultType == TestResult.ResultType.SKIPPED) {
          testResult.setIgnored()
        } else {
          val failures = collectedResult.failures
          for (failure in failures) {
            testResult.addFailure(failure)
          }
        }
      }
    }
    return model
  }

  private fun generateFiles(model: AllTestResults, resultsProvider: TestResultsProvider, reportDir: File) {
    try {
      val htmlRenderer = HtmlReportRenderer()
      buildOperationRunner.run(object : RunnableBuildOperation {
        override fun run(context: BuildOperationContext) {
          // Clean-up old HTML report directories
          File(reportDir, "packages").deleteRecursively()
          File(reportDir, "classes").deleteRecursively()
        }

        override fun description(): BuildOperationDescriptor.Builder {
          return BuildOperationDescriptor.displayName("Delete old HTML results")
        }
      })

      htmlRenderer.render(
        model,
        object : ReportRenderer<AllTestResults, HtmlReportBuilder>() {
          @Throws(IOException::class)
          override fun render(model: AllTestResults, output: HtmlReportBuilder) {
            buildOperationExecutor.runAll { queue ->
              queue.add(
                generator("index.html", model, OverviewPageRenderer(), output)
              )
              for (packageResults in model.getPackages()) {
                queue.add(
                  generator(packageResults.baseUrl, packageResults, PackagePageRenderer(), output)
                )
                for (classResults in packageResults.getClasses()) {
                  queue.add(
                    generator(
                      classResults.baseUrl,
                      classResults,
                      ClassPageRenderer(
                        resultsProvider,
                        diffRegistryFactory
                      ),
                      output
                    )
                  )
                }
              }
            }
          }
        },
        reportDir
      )
    } catch (e: Exception) {
      throw GradleException(String.format("Could not generate test report to '%s'.", reportDir), e)
    }
  }

  class HtmlReportFileGenerator<T : CompositeTestResults> internal constructor(
    private val fileUrl: String,
    private val results: T,
    private val renderer: PageRenderer<T>,
    private val output: HtmlReportBuilder
  ) : RunnableBuildOperation {
    override fun description(): BuildOperationDescriptor.Builder =
      BuildOperationDescriptor.displayName("Generate HTML test report for " + results.title)

    override fun run(context: BuildOperationContext) {
      output.renderHtmlPage(fileUrl, results, renderer)
    }
  }

  companion object {
    private val LOG: Logger = Logging.getLogger(PaparazziTestReporter::class.java)

    fun <T : CompositeTestResults> generator(
      fileUrl: String,
      results: T,
      renderer: PageRenderer<T>,
      output: HtmlReportBuilder
    ): HtmlReportFileGenerator<T> = HtmlReportFileGenerator(fileUrl, results, renderer, output)
  }
}
