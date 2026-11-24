package app.cash.paparazzi.gradle.reporting

import org.gradle.api.Action

internal abstract class ErroringAction<T : Any> : Action<T> {
  override fun execute(thing: T) {
    try {
      doExecute(thing)
    } catch (e: Exception) {
      if (e is RuntimeException) throw e
      throw RuntimeException(e)
    }
  }

  @Throws(Exception::class)
  protected abstract fun doExecute(thing: T)
}
