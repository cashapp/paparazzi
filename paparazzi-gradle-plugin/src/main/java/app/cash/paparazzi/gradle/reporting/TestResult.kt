package app.cash.paparazzi.gradle.reporting

import com.android.build.gradle.internal.test.report.TestResultModel

/**
 * Custom test result based on Gradle's TestResult
 */
internal class TestResult(
  val name: String,
  private val duration: Long,
  val project: String,
  private val flavor: String,
  var screenshotDiffImages: List<ScreenshotDiffImage>?,
  val classResults: ClassTestResults
) : TestResultModel(), Comparable<TestResult> {

  internal val failures: MutableList<TestFailure> = ArrayList()
  internal val errors: MutableList<TestError> = ArrayList()
  private var ignored = false

  internal val id: Any
    get() = name

  private val title = String.format("Test %s", name)

  override fun getResultType(): org.gradle.api.tasks.testing.TestResult.ResultType {
    if (ignored) {
      return org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED
    }
    return if (failures.isEmpty() && errors.isEmpty()) org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS else org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
  }

  override fun getDuration(): Long = duration
  override fun getTitle(): String = title

  override fun getFormattedDuration(): String {
    return if (ignored) "-" else super.getFormattedDuration()
  }

  internal fun addFailure(
    message: String,
    stackTrace: String,
    exceptionType: String?,
    projectName: String,
    flavorName: String
  ) {
    classResults.failed(this, projectName, flavorName)
    failures.add(
      TestFailure(
        message,
        stackTrace,
        exceptionType
      )
    )
  }

  internal fun addError(
    message: String,
    projectName: String,
    flavorName: String
  ) {
    classResults.error(this, projectName, flavorName)
    errors.add(
      TestError(
        message
      )
    )
  }

  internal fun ignored(projectName: String, flavorName: String) {
    ignored = true
    classResults.skipped(projectName, flavorName)
  }

  override fun compareTo(other: TestResult): Int {
    var diff: Int = classResults.name.compareTo(other.classResults.name)
    if (diff != 0) {
      return diff
    }
    diff = name.compareTo(other.name)
    if (diff != 0) {
      return diff
    }
    diff = flavor.compareTo(other.flavor)
    if (diff != 0) {
      return diff
    }
    val thisIdentity = System.identityHashCode(this)
    val otherIdentity = System.identityHashCode(other)
    return thisIdentity.compareTo(otherIdentity)
  }

  internal data class TestFailure(
    val message: String,
    val stackTrace: String?,
    val exceptionType: String?
  )

  internal data class TestError(val message: String)
}
