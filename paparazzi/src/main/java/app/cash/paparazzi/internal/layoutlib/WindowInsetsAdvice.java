package app.cash.paparazzi.internal.layoutlib;

import net.bytebuddy.asm.Advice;

/** Advice for Layoutlib's synthetic preview window, not application-created windows. */
final class WindowInsetsAdvice {
  private WindowInsetsAdvice() {}

  static final class SetContentView {
    private SetContentView() {}

    @Advice.OnMethodEnter
    static void enter(
        @Advice.This android.view.Window window,
        @Advice.Argument(0) android.view.View content) {
      WindowInsetsSupport.configurePreviewWindow(window, content);
    }
  }
}
