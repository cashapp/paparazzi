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

  /**
   * The canvas size `RenderSessionImpl.measureLayout` last settled on, or 0 before the first one.
   * Windows are positioned inside this rather than inside the device display.
   */
  @JvmField
  var canvasWidth: Int = 0

  @JvmField
  var canvasHeight: Int = 0

  fun reset() {
    canvasSizedForContent = true
    canvasWidth = 0
    canvasHeight = 0
  }
}
