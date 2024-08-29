package app.cash.paparazzi.gradle.reporting

import org.gradle.api.Action
import org.gradle.internal.UncheckedException

/**
 * Custom ErroringAction based on Gradle's ErroringAction
 */
internal abstract class ErroringAction<T : Any> : Action<T> {
  override fun execute(thing: T) {
    try {
      doExecute(thing)
    } catch (var3: Exception) {
      throw UncheckedException.throwAsUncheckedException(var3)
    }
  }

  @Throws(Exception::class)
  protected abstract fun doExecute(var1: T)
}
