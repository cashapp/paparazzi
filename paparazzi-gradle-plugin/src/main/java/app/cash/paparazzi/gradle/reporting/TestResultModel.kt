package app.cash.paparazzi.gradle.reporting

import org.gradle.api.tasks.testing.TestResult

internal abstract class TestResultModel {
  abstract val resultType: TestResult.ResultType
  abstract val duration: Long
  abstract val title: String

  open val formattedDuration: String
    get() = DURATION_FORMATTER.format(duration)

  val statusClass: String
    get() = when (resultType) {
      TestResult.ResultType.SUCCESS -> "success"
      TestResult.ResultType.FAILURE -> "failures"
      TestResult.ResultType.SKIPPED -> "skipped"
      else -> throw IllegalStateException()
    }

  val formattedResultType: String
    get() = when (resultType) {
      TestResult.ResultType.SUCCESS -> "passed"
      TestResult.ResultType.FAILURE -> "failed"
      TestResult.ResultType.SKIPPED -> "ignored"
      else -> throw IllegalStateException()
    }

  companion object {
    val DURATION_FORMATTER: DurationFormatter = DurationFormatter()
  }
}
