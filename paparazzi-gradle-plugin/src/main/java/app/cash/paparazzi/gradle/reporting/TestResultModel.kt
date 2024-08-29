package app.cash.paparazzi.gradle.reporting

import com.android.build.gradle.internal.test.report.DurationFormatter

internal abstract class TestResultModel {
  abstract val duration: Long
  abstract val title: String

  val statusClass: String
    get() = when (getResultType()) {
      ResultType.SUCCESS -> "success"
      ResultType.FAILURE -> "failures"
      ResultType.ERROR -> "errors"
      ResultType.SKIPPED -> "skipped"
    }

  abstract fun getResultType(): ResultType

  open fun getFormattedDuration(): String {
    return DURATION_FORMATTER.format(duration)
  }

  fun getFormattedResultType(): String {
    return when (getResultType()) {
      ResultType.SUCCESS -> "passed"
      ResultType.FAILURE -> "failed"
      ResultType.ERROR -> "error"
      ResultType.SKIPPED -> "ignored"
    }
  }

  companion object {
    val DURATION_FORMATTER: DurationFormatter = DurationFormatter()
  }

  enum class ResultType {
    SUCCESS,
    FAILURE,
    ERROR,
    SKIPPED
  }
}
