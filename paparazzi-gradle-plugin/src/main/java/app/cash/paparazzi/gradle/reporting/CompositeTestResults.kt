package app.cash.paparazzi.gradle.reporting

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.TreeMap
import java.util.TreeSet

/**
 * Custom CompositeTestResults based on Gradle's CompositeTestResults
 */
internal abstract class CompositeTestResults protected constructor(
  private val parent: CompositeTestResults?
) : TestResultModel() {

  internal var testCount = 0
    private set
  internal val failures: MutableSet<TestResult> =
    TreeSet<TestResult>()
  internal val errors: MutableSet<TestResult> =
    TreeSet<TestResult>()
  internal val standardOutput = TreeSet<String>()
  internal val standardError = TreeSet<String>()

  internal var skipCount = 0
  override var duration: Long = 0
  private val variants: MutableMap<String, VariantTestResults?> =
    TreeMap<String, VariantTestResults?>()

  internal fun getFilename(): String? {
    return name
  }

  internal abstract val name: String?
  val failureCount: Int
    get() = failures.size
  val errorCount: Int
    get() = errors.size

  override fun getFormattedDuration(): String {
    return if (testCount == 0) "-" else super.getFormattedDuration()
  }

  override fun getResultType(): ResultType {
    return if (failures.isEmpty() && errorCount == 0) ResultType.SUCCESS else ResultType.FAILURE
  }

  val formattedSuccessRate: String
    get() {
      val successRate = successRate ?: return "-"
      return "$successRate%"
    }
  val successRate: Number?
    get() {
      if (testCount == 0 || testCount == skipCount) {
        return null
      }
      val tests = BigDecimal.valueOf((testCount - skipCount).toLong())
      val successful =
        BigDecimal.valueOf((testCount - failureCount - skipCount - errorCount).toLong())
      return successful.divide(
        tests, 2,
        RoundingMode.DOWN
      ).multiply(BigDecimal.valueOf(100)).toInt()
    }

  internal fun failed(
    failedTest: TestResult,
    projectName: String,
    flavorName: String
  ) {
    failures.add(failedTest)
    parent?.failed(failedTest, projectName, flavorName)
    val key: String =
      getVariantKey(
        projectName,
        flavorName
      )
    variants[key]?.failed(failedTest, projectName, flavorName)
  }

  fun error(
    testError: TestResult,
    projectName: String,
    flavorName: String
  ) {
    errors.add(testError)
    parent?.error(testError, projectName, flavorName)
    val key: String =
      getVariantKey(
        projectName,
        flavorName
      )
    variants[key]?.error(testError, projectName, flavorName)
  }

  fun skipped(projectName: String, flavorName: String) {
    skipCount++
    parent?.skipped(projectName, flavorName)
    val key: String =
      getVariantKey(
        projectName,
        flavorName
      )
    variants[key]?.skipped(projectName, flavorName)
  }

  fun addStandardOutput(textContent: String) {
    standardOutput.add(textContent)
  }

  fun addStandardError(textContent: String) {
    standardError.add(textContent)
  }

  protected fun addTest(test: TestResult): TestResult {
    testCount++
    duration += test.duration
    return test
  }

  internal fun addVariant(
    projectName: String,
    flavorName: String,
    testResult: TestResult
  ) {
    val key: String =
      getVariantKey(
        projectName,
        flavorName
      )
    var variantResults: VariantTestResults? = variants[key]
    if (variantResults == null) {
      variantResults = VariantTestResults(key, null)
      variants[key] = variantResults
    }
    variantResults.addTest(testResult)
  }

  companion object {
    private fun getVariantKey(projectName: String, flavorName: String): String {
      return if (flavorName.equals("main", ignoreCase = true)) {
        projectName
      } else {
        "$projectName:$flavorName"
      }
    }
  }

  abstract override val title: String
}
