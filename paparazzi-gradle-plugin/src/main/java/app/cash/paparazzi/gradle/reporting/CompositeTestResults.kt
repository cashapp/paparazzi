package app.cash.paparazzi.gradle.reporting

import org.gradle.api.tasks.testing.TestResult.ResultType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.TreeSet
import kotlin.math.min

internal abstract class CompositeTestResults protected constructor(
  val parent: CompositeTestResults?
) : TestResultModel() {
  var testCount: Int = 0
    private set
  private val _failures = TreeSet<TestResult>()
  private val _ignored = TreeSet<TestResult>()
  public final override var duration: Long = 0
    private set

  abstract val baseUrl: String

  fun getUrlTo(model: CompositeTestResults): String {
    val otherUrl = model.baseUrl
    val thisUrl = baseUrl

    val maxPos = min(thisUrl.length, otherUrl.length)
    var endPrefix = 0
    while (endPrefix < maxPos) {
      val endA = thisUrl.indexOf('/', endPrefix)
      val endB = otherUrl.indexOf('/', endPrefix)
      if (endA != endB || endA < 0) {
        break
      }
      if (!thisUrl.regionMatches(endPrefix, otherUrl, endPrefix, endA - endPrefix)) {
        break
      }
      endPrefix = endA + 1
    }

    return buildString {
      var endA = endPrefix
      while (endA < thisUrl.length) {
        val pos = thisUrl.indexOf('/', endA)
        if (pos < 0) {
          break
        }
        append("../")
        endA = pos + 1
      }
      append(otherUrl.substring(endPrefix))
    }
  }

  val failureCount: Int
    get() = _failures.size

  val ignoredCount: Int
    get() = _ignored.size

  private val runTestCount: Int
    get() = testCount - ignoredCount

  override val formattedDuration: String
    get() = if (testCount == 0) "-" else super.formattedDuration

  val failures: Set<TestResult>
    get() = _failures

  val ignored: Set<TestResult>
    get() = _ignored

  override val resultType: ResultType
    get() =
      if (_failures.isNotEmpty()) {
        ResultType.FAILURE
      } else if (ignoredCount > 0) {
        ResultType.SKIPPED
      } else {
        ResultType.SUCCESS
      }

  val formattedSuccessRate: String
    get() {
      val successRate = successRate ?: return "-"
      return "$successRate%"
    }

  val successRate: Number?
    get() {
      if (runTestCount == 0) return null
      val runTests = BigDecimal.valueOf(runTestCount.toLong())
      val successful = BigDecimal.valueOf((runTestCount - failureCount).toLong())
      return successful.divide(runTests, 2, RoundingMode.DOWN)
        .multiply(BigDecimal.valueOf(100)).toInt()
    }

  fun failed(failedTest: TestResult) {
    _failures += failedTest
    parent?.failed(failedTest)
  }

  fun ignored(ignoredTest: TestResult) {
    _ignored += ignoredTest
    parent?.ignored(ignoredTest)
  }

  protected fun addTest(test: TestResult): TestResult {
    testCount++
    duration += test.duration
    return test
  }
}
