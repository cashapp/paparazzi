package app.cash.paparazzi.gradle.reporting

import com.github.javaparser.ParseException
import org.gradle.api.GradleException
import org.gradle.reporting.HtmlReportBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Custom test reporter based on Gradle's DefaultTestReport
 */
internal class TestReport(
  private val resultDir: File,
  private val reportDir: File,
  private val failureSnapshotDir: File?,
  private val applicationId: String,
  private val variantKey: String
) {

  private val htmlRenderer: HtmlReportRenderer = HtmlReportRenderer()
  private val diffImages = mutableMapOf<String, List<ScreenshotDiffImage>>()

  init {
    // TODO: Maybe include these in repo?
    htmlRenderer.requireResource(HtmlReportBuilder::class.java.getResource("report.js")!!)
    htmlRenderer.requireResource(HtmlReportBuilder::class.java.getResource("base-style.css")!!)
    // Included in repo
    htmlRenderer.requireResource(javaClass.getResource("style.css")!!)
  }

  fun generateScreenshotTestReport(): CompositeTestResults {
    processFailedImageDiffs()
    return loadModel().also(::generateFilesForScreenshotTest)
  }

  private fun processFailedImageDiffs() {
    failureSnapshotDir
      ?.listFiles()
      ?.filter {
        val nameSegments = it.name.split("_", limit = 3)
        it.name.startsWith("delta-") && nameSegments.size == 3
      }
      ?.forEach { diff ->
        val nameSegments = diff.name.split("_", limit = 3)
        val testClassPackage = nameSegments[0].replace("delta-", "")
        val testClass = "$testClassPackage.${nameSegments[1]}"
        val testMethodWithLabel = nameSegments[2].split(".")[0].split("-")
        val testMethod = testMethodWithLabel[0]
        val label = testMethodWithLabel.getOrNull(1)

        val diffImage = ScreenshotDiffImage(
          diff.path,
          testClassPackage,
          testClass,
          testMethod,
          label
        )

        val key = "${testClass}_$testMethod"
        val images = diffImages.getOrDefault(key, listOf())
        diffImages[key] = images + diffImage
      }
  }

  private fun loadModel(): AllTestResults {
    val model = AllTestResults()
    if (resultDir.exists()) {
      resultDir.listFiles()?.forEach { file: File ->
        if (file.name.startsWith("TEST-") && file.getName().endsWith(".xml")) {
          mergeFromFile(file, model)
        }
      }
    }
    return model
  }

  private fun mergeFromFile(
    file: File,
    model: AllTestResults
  ) {
    var inputStream: InputStream? = null
    try {
      inputStream = FileInputStream(file)
      val document: Document = try {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
          InputSource(inputStream)
        )
      } finally {
        inputStream.close()
      }

      val testCases = document.getElementsByTagName("testcase")
      val systemOut = document.getElementsByTagName("system-out").item(0).textContent
      val systemErr = document.getElementsByTagName("system-err").item(0).textContent

      model.addStandardOutput(systemOut)
      model.addStandardError(systemErr)

      for (i in 0 until testCases.length) {
        val testCase = testCases.item(i) as Element
        val className = testCase.getAttribute("classname")
        val testName = testCase.getAttribute("name")
        val timeString = testCase.getAttribute("time")
        var duration =
          if (timeString.isNotBlank()) parse(timeString) else BigDecimal.valueOf(0)
        duration = duration.multiply(BigDecimal.valueOf(1000))
        val failures = testCase.getElementsByTagName("failure")
        val errors = testCase.getElementsByTagName("error")

        val diffImage: List<ScreenshotDiffImage>? = diffImages["${className}_$testName"]

        val testResult: TestResult = model.addTest(
          className = className,
          testName = testName,
          duration = duration.toLong(),
          project = applicationId,
          flavor = variantKey,
          diffImages = diffImage
        )

        for (j in 0 until failures.length) {
          val failure = failures.item(j) as Element
          testResult.addFailure(
            message = failure.getAttribute("message"),
            stackTrace = failure.textContent,
            projectName = applicationId,
            exceptionType = failure.getAttribute("type"),
            flavorName = variantKey
          )
        }
        for (j in 0 until errors.length) {
          val error = errors.item(j) as Element
          testResult.addError(error.textContent, applicationId, variantKey)
        }
        if (testCase.getElementsByTagName("skipped").length > 0) {
          testResult.ignored(applicationId, variantKey)
        }
      }
      val ignoredTestCases = document.getElementsByTagName("ignored-testcase")
      for (i in 0 until ignoredTestCases.length) {
        val testCase = ignoredTestCases.item(i) as Element
        val className = testCase.getAttribute("classname")
        val testName = testCase.getAttribute("name")
        model.addTest(className, testName, 0, applicationId, variantKey, null)
          .ignored(applicationId, variantKey)
      }
      val suiteClassName = document.documentElement.getAttribute("name")
      if (suiteClassName.isNotBlank()) {
        model.addTestClass(suiteClassName)
      }
    } catch (e: Exception) {
      throw GradleException(String.format("Could not load test results from '%s'.", file), e)
    } finally {
      try {
        inputStream?.close()
      } catch (e: IOException) {
        // cannot happen
      }
    }
  }

  private fun generateFilesForScreenshotTest(
    model: AllTestResults
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

  /**
   * Regardless of the default locale, comma ('.') is used as decimal separator
   *
   * @param source
   * @return
   * @throws java.text.ParseException
   */
  @Throws(ParseException::class)
  fun parse(source: String?): BigDecimal {
    val symbols = DecimalFormatSymbols()
    symbols.setDecimalSeparator('.')
    val format = DecimalFormat("#.#", symbols)
    format.isParseBigDecimal = true
    return format.parse(source) as BigDecimal
  }
}
