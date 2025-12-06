package app.cash.paparazzi.internal.validation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

/**
 * Checks if the [MainCoroutineDispatcher] within [Dispatchers.Main] has
 * been tampered with in a Paparazzi test.
 *
 * By default, Paparazzi's [MainCoroutineDispatcher] uses a `HandlerContext.`
 *
 * If someone uses `Dispatchers.setMain(...)` within a Paparazzi test, there is a chance
 * that they make the test pass, but not really execute some of the scheduled coroutines within
 * the test dispatcher. This in turn can pile up in large modules with lots of snapshot tests, and
 * eventually can cause `java.lang.OutOfMemoryError.`
 *
 * Setting the `Dispatchers.Main` to a `TestDispatcher` can also lead to flaky tests if the developer
 * doesn't properly call `advanceUntilIdle()`.
 *
 * Finally, Paparazzi already has access to its own `AndroidUiDispatcher,` so there
 * aren't many benefits to use `Dispatchers.setMain()` within Paparazzi tests.
 *
 * @param defaultHandlerClassName The default accepted class name for the [MainCoroutineDispatcher].
 * @param strictMode Allow custom dispatchers by default and let developers opt-in optionally with strict mode.
 */
internal class MainDispatcherValidator(
  private val defaultHandlerClassName: String = "HandlerContext",
  private val strictMode: Boolean
) {

  private val errorMessage = "Main dispatcher was modified." +
    " Please do not change Main dispatcher in paparazzi tests." +
    "If overriding Main dispatched is needed, please call " +
    "\"Dispatchers.resetMain()\" before Paparazzi.snapshot or Paparazzi.gif is called."

  internal fun checkMainCoroutineDispatcher() {
    if (isMainDispatcherModified()) {
      throw MainDispatcherAlteredException(
        message = errorMessage
      )
    }
  }

  private fun isMainDispatcherModified(): Boolean {
    return try {
      strictMode && !Dispatchers.Main.immediate.javaClass.name.contains(defaultHandlerClassName, ignoreCase = true)
    } catch (_: Exception) {
      // If an exception happens when trying to access Dispatchers.Main then that means it was
      // never altered to begin with.
      false
    }
  }

  private class MainDispatcherAlteredException(message: String) : IllegalStateException(message)
}
