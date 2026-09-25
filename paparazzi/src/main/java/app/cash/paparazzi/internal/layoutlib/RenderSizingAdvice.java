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
   * {@code com.android.layoutlib.bridge.impl.RenderSessionImpl#measureLayout(SessionParams)}, the
   * point at which the canvas size has been derived from the current content. Any traversal from
   * here on is no longer premature.
   */
  static final class MeasureLayoutComplete {
    private MeasureLayoutComplete() {}

    @Advice.OnMethodExit
    static void exit() {
      RenderSizingState.canvasSizedForContent = true;
    }
  }
}
