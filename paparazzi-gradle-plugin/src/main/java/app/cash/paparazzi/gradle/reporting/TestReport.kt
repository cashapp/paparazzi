package app.cash.paparazzi.gradle.reporting

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.internal.tasks.testing.report.TestReporter
import org.gradle.reporting.HtmlReportBuilder
import java.io.File
import java.util.Base64

/**
 * Custom test reporter based on Gradle's DefaultTestReport
 */
internal class TestReport(
  private val failureSnapshotDir: File?,
  private val applicationId: String,
  private val variantKey: String
) : TestReporter {

  private val htmlRenderer: HtmlReportRenderer = HtmlReportRenderer()
  private lateinit var diffImages: List<File>
  private val base64Encoder = Base64.getEncoder()

  init {
    // TODO: Maybe include these in repo?
    htmlRenderer.requireResource(HtmlReportBuilder::class.java.getResource("report.js")!!)
    htmlRenderer.requireResource(HtmlReportBuilder::class.java.getResource("base-style.css")!!)
    // Included in repo
    htmlRenderer.requireResource(javaClass.getResource("style.css")!!)
  }

  override fun generateReport(testResultsProvider: TestResultsProvider, reportDir: File) {
    processFailedImageDiffs()

    val model = AllTestResults()
    testResultsProvider.visitClasses {
      val testClass = it.className
      model.duration = it.duration

      it.results.forEach { testResult ->
        val diffImage: List<ScreenshotDiffImage> = getScreenshotDiffs(testClass, testResult.name)

        model.addTest(
          className = testClass,
          testName = testResult.name,
          duration = testResult.duration,
          project = applicationId,
          flavor = variantKey,
          diffImages = diffImage
        ).apply {
          testResult.failures.forEach { failure ->
            addFailure(
              message = failure.message,
              stackTrace = failure.stackTrace,
              projectName = applicationId,
              exceptionType = failure.exceptionType,
              flavorName = variantKey
            )
          }

          if (testResult.resultType == org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED) {
            ignored(applicationId, variantKey)
          }
        }
      }
    }

    generateFilesForScreenshotTest(model, reportDir)
  }

  private fun getScreenshotDiffs(className: String, methodName: String) =
    diffImages.mapNotNull { diff ->
      val nameSegments = diff.name.split("_", limit = 3)
      val testClassPackage = nameSegments[0].replace("delta-", "")
      val testClass = "$testClassPackage.${nameSegments[1]}"

      if (testClass != className) return@mapNotNull null

      val testMethodWithLabel = nameSegments[2].split(".")[0]
      var testMethod: String? = null
      "(${Regex.escape(methodName)})_?(.*)".toRegex().find(testMethodWithLabel)?.let {
        testMethod = it.groupValues.getOrNull(1)?.toString()
      }

      if (testMethod != methodName) return@mapNotNull null
      requireNotNull(testMethod) {
        "Test method should be defined in snapshot filename ${diff.name}"
      }

      return@mapNotNull ScreenshotDiffImage(
        path = diff.path,
        snapshotName = diff.name.replace("delta-", ""),
        base64EncodedImage = base64Encoder.encodeToString(diff.readBytes())
      )
    }

  private fun processFailedImageDiffs() {
    diffImages = failureSnapshotDir
      ?.listFiles()
      ?.filter {
        it.name.startsWith("delta-")
      }
      ?: emptyList()
  }

  private fun generateFilesForScreenshotTest(
    model: AllTestResults,
    reportDir: File
  ) {
    try {
      generatePage(
        model,
        OverviewPageRenderer(),
        File(reportDir, "index.html")
      )
      for (packageResults in model.getPackages()) {
        generatePage(
          packageResults,
          PackagePageRenderer(),
          File(reportDir, packageResults.getFilename() + ".html")
        )
        for (classResults in packageResults.getClasses()) {
          generatePage(
            classResults!!,
            ScreenshotClassPageRenderer(),
            File(reportDir, classResults.getFilename() + ".html")
          )
        }
      }
    } catch (e: Exception) {
      throw GradleException(
        String.format(
          "Could not generate test report to '%s'.",
          reportDir
        ),
        e
      )
    }
  }

  @Throws(Exception::class)
  private fun <T : CompositeTestResults> generatePage(
    model: T,
    renderer: PageRenderer<T>,
    outputFile: File
  ) {
    htmlRenderer.renderer(renderer).writeTo(model, outputFile)
  }
}
