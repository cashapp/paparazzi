package app.cash.paparazzi.gradle.reporting

import java.math.BigDecimal
import java.math.RoundingMode

internal class DurationFormatter {
  fun format(duration: Long): String {
    @Suppress("NAME_SHADOWING")
    var duration = duration
    if (duration == 0L) {
      return "0s"
    }

    return buildString {
      val days = duration / MILLIS_PER_DAY
      duration %= MILLIS_PER_DAY
      if (days > 0) {
        append(days)
        append("d")
      }

      val hours = duration / MILLIS_PER_HOUR
      duration %= MILLIS_PER_HOUR
      if (hours > 0 || isNotEmpty()) {
        append(hours)
        append("h")
      }

      val minutes = duration / MILLIS_PER_MINUTE
      duration %= MILLIS_PER_MINUTE
      if (minutes > 0 || isNotEmpty()) {
        append(minutes)
        append("m")
      }
      val secondsScale = if (isNotEmpty()) 2 else 3
      append(
        BigDecimal.valueOf(duration).divide(BigDecimal.valueOf(MILLIS_PER_SECOND.toLong()))
          .setScale(secondsScale, RoundingMode.HALF_UP)
      )
      append("s")
    }
  }

  companion object {
    const val MILLIS_PER_SECOND: Int = 1000
    const val MILLIS_PER_MINUTE: Int = 60 * MILLIS_PER_SECOND
    const val MILLIS_PER_HOUR: Int = 60 * MILLIS_PER_MINUTE
    const val MILLIS_PER_DAY: Int = 24 * MILLIS_PER_HOUR
  }
}
