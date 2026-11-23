package app.cash.paparazzi.internal.layoutlib;

import com.android.layoutlib.bridge.util.HandlerMessageQueue;
import net.bytebuddy.asm.Advice;

/** Installed before a render session can post its first callback. */
final class CallbackQueueAdvice {
  private CallbackQueueAdvice() {}

  static final class SessionCreated {
    private SessionCreated() {}

    @Advice.OnMethodExit
    static void exit(
        @Advice.FieldValue(value = "mHandlerMessageQueue", readOnly = false)
            HandlerMessageQueue queue) {
      if (queue.size() != 0) {
        throw new IllegalStateException("Cannot replace a Layoutlib callback queue after posting");
      }
      queue = new DeterministicHandlerMessageQueue();
    }
  }
}
