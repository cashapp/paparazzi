package app.cash.paparazzi.internal.layoutlib;

import net.bytebuddy.asm.Advice;

/**
 * The three {@link Advice} fragments {@link LayoutlibPatch} inlines into layoutlib.
 *
 * <p>Advice bodies are copied into the target method, so they may only reference members that are
 * public in the class file. None of these classes is ever loaded at runtime; only their bytecode is
 * read.
 *
 * <p>This file is Java because a copied body has to be genuinely {@code static}: Kotlin
 * {@code object} members are instance methods, and Kotlin injects {@code Intrinsics} null-checks
 * and synthetic accessors. {@link RenderSizingState} is safely Kotlin because it is only
 * referenced, never inlined, and its {@code @JvmField}s compile to plain statics.
 */
final class RenderSizingAdvice {
  private RenderSizingAdvice() {}

  /**
   * Inlined at the head of {@code android.view.ViewRootImpl#performTraversals()}.
   *
   * <p>{@code RenderSessionImpl} establishes the canvas size in {@code measureLayout} and only then
   * runs a traversal. A traversal driven from the {@code Choreographer} instead runs against
   * whatever {@code mWinFrame} happens to hold, and dispatches {@code onPreDraw} at that size. In
   * the expanding rendering modes that is observable: {@code measureLayout}'s {@code UNSPECIFIED}
   * probe is meant to discover the content's natural size, and by then the content has already been
   * told its size is the stale one. Skipping the body is safe because {@code renderAndBuildResult}
   * performs the traversal itself immediately after {@code measureLayout} + {@code updateFrame};
   * {@code doTraversal} has already cleared {@code mTraversalScheduled} and removed the sync
   * barrier by the time this runs, and {@code mLayoutRequested} / {@code mFullRedrawNeeded} are
   * left set for that traversal.
   */
  static final class SuppressPrematureTraversal {
    private SuppressPrematureTraversal() {}

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter() {
      return !RenderSizingState.canvasSizedForContent;
    }
  }

  /**
   * Inlined at the tail of
   * {@code com.android.layoutlib.bridge.impl.RenderSessionImpl#measureLayout(SessionParams)},
   * which is where the canvas size is decided. See
   * {@link WindowSizingSupport#sizeCanvasForWindows}.
   */
  static final class MeasureLayoutComplete {
    private MeasureLayoutComplete() {}

    @Advice.OnMethodExit
    static void exit(
        @Advice.Argument(0) com.android.ide.common.rendering.api.SessionParams params,
        @Advice.FieldValue("mViewRoot") android.view.ViewGroup viewRoot,
        @Advice.FieldValue(value = "mMeasuredScreenWidth", readOnly = false) int width,
        @Advice.FieldValue(value = "mMeasuredScreenHeight", readOnly = false) int height,
        @Advice.FieldValue(value = "mNewRenderSize", readOnly = false) boolean newRenderSize) {
      // measureLayout has just derived the canvas from the current content, so any traversal from
      // here on is no longer premature.
      RenderSizingState.canvasSizedForContent = true;
      long size = WindowSizingSupport.sizeCanvasForWindows(params, viewRoot, width, height);
      int sizedWidth = (int) (size >> 32);
      int sizedHeight = (int) size;
      // measureLayout derives mNewRenderSize from the size it computed, before this runs.
      newRenderSize = newRenderSize || sizedWidth != width || sizedHeight != height;
      width = sizedWidth;
      height = sizedHeight;
    }
  }

  /**
   * Inlined at the tail of
   * {@code com.android.layoutlib.bridge.impl.BridgeWindowSession#relayout}. See
   * {@link WindowSizingSupport#positionWindowInCanvas}.
   */
  static final class WindowRelayout {
    private WindowRelayout() {}

    @Advice.OnMethodExit
    static void exit(
        @Advice.Argument(1) android.view.WindowManager.LayoutParams attrs,
        @Advice.Argument(2) int requestedWidth,
        @Advice.Argument(3) int requestedHeight,
        @Advice.Argument(8) android.view.WindowRelayoutResult result) {
      WindowSizingSupport.positionWindowInCanvas(attrs, requestedWidth, requestedHeight, result);
    }
  }
}
