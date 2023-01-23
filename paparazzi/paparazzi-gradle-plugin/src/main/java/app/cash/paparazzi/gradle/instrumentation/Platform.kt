package app.cash.paparazzi.gradle.instrumentation

import java.io.Serializable

sealed interface Platform : Serializable {
  object Windows : Platform
  object UnixLike : Platform
}
