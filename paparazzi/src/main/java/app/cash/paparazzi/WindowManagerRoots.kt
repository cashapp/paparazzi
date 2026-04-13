package app.cash.paparazzi

import android.view.View
import android.view.WindowManager
import android.view.WindowManagerGlobal

internal fun WindowManagerGlobal.findPopupRootView(excludedView: View): View? {
  @Suppress("UNCHECKED_CAST")
  val params = WindowManagerGlobal::class.java
    .getFieldReflectively("mParams")
    .get(this) as List<WindowManager.LayoutParams>
  return windowViews
    .asSequence()
    .zip(params.asSequence())
    .drop(1)
    .lastOrNull { (view, layoutParams) ->
      view !== excludedView && layoutParams.token != null
    }
    ?.first
}
