package app.cash.paparazzi.internal.layoutlib

import android.graphics.Color
import android.view.IWindow
import android.view.InsetsState
import android.view.View
import android.view.ViewGroup
import android.view.ViewRootImpl
import android.view.Window
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.view.WindowRelayoutResult
import com.android.layoutlib.bridge.impl.Layout

/**
 * Compatibility with Layoutlib 16.2.4's synthetic preview window.
 *
 * Layoutlib seeds its client's inset state, but BridgeWindowSession.relayout returns an empty
 * state. Real framework traversals then discard the seeded sources. This started with
 * https://android.googlesource.com/platform/frameworks/layoutlib/+/ab12e671f5d2be99a9a9a35034df5354c9c4f51d.
 * Retire this workaround when Layoutlib returns the correct state itself. A non-empty upstream
 * reply is never replaced here.
 */
internal object WindowInsetsSupport {
  @JvmStatic
  fun configurePreviewWindow(window: Window?, content: View?) {
    if (window != null && content is Layout && content.insetsFrameProviders.isNotEmpty()) {
      // Layout already owns system-UI placement. The additional PhoneWindow must not inset the
      // preview content a second time. Configure it before the initial content layout.
      // Paparazzi uses simulated API 0/latest, for which Layout renders edge-to-edge. Revisit this
      // policy if Paparazzi exposes older simulated platform versions.
      window.setDecorFitsSystemWindows(false)
      // Layout owns bar drawing too. Keep the flag to avoid legacy/forced black bar backgrounds,
      // but make the extra decor's colors transparent, including on target SDK 24 and higher.
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.clearFlags(
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
          WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
      )
      window.statusBarColor = Color.TRANSPARENT
      window.navigationBarColor = Color.TRANSPARENT
      window.navigationBarDividerColor = Color.TRANSPARENT
      window.isStatusBarContrastEnforced = false
      window.isNavigationBarContrastEnforced = false
    }
  }

  @JvmStatic
  fun captureInsets(window: IWindow?): InsetsState? {
    val token = window?.asBinder() ?: return null
    for (view in WindowManagerGlobal.getInstance().windowViews) {
      val root = view.parent as? ViewRootImpl ?: continue
      if (root.windowToken !== token) continue
      val layout = findPreviewLayout(view) ?: return null
      if (layout.insetsFrameProviders.isEmpty()) return null
      val state = root.insetsController.state
      if (state.sourceSize() == 0) return null
      // Copy before relayout replaces the client's state. Do not retain windows across sessions,
      // or apply the main window's state to a dialog/popup with a different frame.
      return InsetsState(state, true)
    }
    return null
  }

  @JvmStatic
  fun clearReply(saved: InsetsState?, result: WindowRelayoutResult?) {
    if (saved != null && result != null) {
      // ViewRootImpl reuses this output. Remove our previous copy before calling upstream, so a
      // non-empty reply on exit really was populated by upstream, not left over from our last call.
      result.insetsState.set(InsetsState(), true)
    }
  }

  @JvmStatic
  fun restoreInsets(saved: InsetsState?, result: WindowRelayoutResult?) {
    if (saved != null && result != null && result.insetsState.sourceSize() == 0) {
      result.insetsState.set(saved, true)
    }
  }

  private fun findPreviewLayout(view: View): Layout? {
    if (view is Layout) return view
    if (view is ViewGroup) {
      for (index in 0 until view.childCount) {
        findPreviewLayout(view.getChildAt(index))?.let { return it }
      }
    }
    return null
  }
}
