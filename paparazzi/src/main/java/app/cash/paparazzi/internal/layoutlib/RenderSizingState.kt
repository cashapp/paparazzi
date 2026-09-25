package app.cash.paparazzi.internal.layoutlib

/**
 * Single-threaded state shared between Paparazzi and the [net.bytebuddy.asm.Advice] fragments that
 * [LayoutlibPatch] inlines into layoutlib.
 *
 * Every member here ends up read from bytecode that lives inside `android.view.ViewRootImpl` and
 * `com.android.layoutlib.bridge.impl.RenderSessionImpl`, so it has to be `public` and unmangled in
 * the class file.
 */
internal object RenderSizingState {
  /**
   * False between the moment new content is attached to the content root and the moment
   * `RenderSessionImpl.measureLayout` next returns, i.e. while
   * `mMeasuredScreenWidth`/`mMeasuredScreenHeight` are stale with respect to the content.
   */
  @JvmField
  var canvasSizedForContent: Boolean = true

  fun reset() {
    canvasSizedForContent = true
  }
}
