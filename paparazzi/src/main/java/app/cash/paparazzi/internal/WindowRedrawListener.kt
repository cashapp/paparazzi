package app.cash.paparazzi.internal

import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowManagerGlobal
import com.android.layoutlib.bridge.android.BridgeContext

/**
 * Keeps additional windows in Layoutlib's root display list during a capture.
 *
 * Layoutlib composites these windows in ThreadedRenderer's onPostDraw callback. A cached root
 * skips that callback, including updates to those windows' display lists. Discarding only the
 * root list lets the existing draw rebuild it, without another frame.
 */
internal class WindowRedrawListener(private val host: View) : OnPreDrawListener {
  private val windowContext = BridgeContext.getBaseContext(host.context)
  private val observer = host.viewTreeObserver
  private var hadAdditionalWindows = false

  fun attach() {
    observer.addOnPreDrawListener(this)
  }

  override fun onPreDraw(): Boolean {
    val hasAdditionalWindows = WindowManagerGlobal.getInstance().windowViews.count {
      BridgeContext.getBaseContext(it.context) === windowContext
    } > 1
    if (hasAdditionalWindows || hadAdditionalWindows) {
      discardRootDisplayList()
    }
    hadAdditionalWindows = hasAdditionalWindows
    return true
  }

  fun detach() {
    val currentObserver = if (observer.isAlive) observer else host.viewTreeObserver
    currentObserver.removeOnPreDrawListener(this)
    if (hadAdditionalWindows) {
      // Do not carry this capture's windows into the next capture's root display list.
      discardRootDisplayList()
    }
  }

  private fun discardRootDisplayList() {
    host.threadedRenderer?.rootNode?.discardDisplayList()
  }
}
