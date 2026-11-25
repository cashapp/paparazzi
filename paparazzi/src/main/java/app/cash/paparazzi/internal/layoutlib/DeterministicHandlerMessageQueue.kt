package app.cash.paparazzi.internal.layoutlib

import android.os.Handler
import com.android.layoutlib.bridge.util.HandlerMessageQueue

/** Keeps Layoutlib's queue API and weak Handler ownership, but breaks timestamp ties globally. */
internal class DeterministicHandlerMessageQueue : HandlerMessageQueue() {
  private val callbacks = WeakCallbackQueue<Handler>()

  override fun add(handler: Handler, uptimeMillis: Long, runnable: Runnable) {
    callbacks.add(handler, uptimeMillis, runnable)
  }

  override fun extractFirst(uptimeMillis: Long): Runnable? = callbacks.extractFirst(uptimeMillis)

  override fun isNotEmpty(): Boolean = callbacks.isNotEmpty()

  override fun size(): Int = callbacks.size()

  override fun clear() = callbacks.clear()
}
