package app.cash.paparazzi.gradle.reporting

import org.gradle.api.internal.tasks.testing.junit.result.TestFailure
import org.gradle.api.tasks.testing.TestResult.ResultType

internal class TestResult(
  val name: String,
  val displayName: String = name,
  override val duration: Long,
  val classResults: ClassTestResults
) : TestResultModel(), Comparable<TestResult> {
  private val _failures: MutableList<TestFailure> = mutableListOf()
  private var isIgnored: Boolean = false

  val id: Any
    get() = name

  override val title: String
    get() = "Test $name"

  override val resultType: ResultType
    get() = when {
      isIgnored -> ResultType.SKIPPED
      _failures.isEmpty() -> ResultType.SUCCESS
      else -> ResultType.FAILURE
    }

  override val formattedDuration: String
    get() = if (isIgnored) "-" else super.formattedDuration

  val failures: List<TestFailure>
    get() = _failures

  fun addFailure(failure: TestFailure) {
    classResults.failed(this)
    _failures += failure
  }

  fun setIgnored() {
    classResults.ignored(this)
    isIgnored = true
  }

  override fun compareTo(other: TestResult): Int {
    var diff = classResults.name.compareTo(other.classResults.name)
    if (diff != 0) {
      return diff
    }

    diff = name.compareTo(other.name)
    if (diff != 0) {
      return diff
    }

    val thisIdentity = System.identityHashCode(this)
    val otherIdentity = System.identityHashCode(other)
    return thisIdentity.compareTo(otherIdentity)
  }
}
