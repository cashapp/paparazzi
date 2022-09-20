package app.cash.paparazzi.gradle

import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Snapshot(
  val name: String?,
  val testName: String,
  val timestamp: Date,
  val tags: List<String> = listOf(),
  val file: String? = null
)
