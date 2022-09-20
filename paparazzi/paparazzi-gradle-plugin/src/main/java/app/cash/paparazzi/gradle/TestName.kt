package app.cash.paparazzi.gradle

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TestName(
  val packageName: String,
  val className: String,
  val methodName: String
)
