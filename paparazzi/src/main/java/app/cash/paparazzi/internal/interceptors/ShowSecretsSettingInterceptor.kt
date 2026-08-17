package app.cash.paparazzi.internal.interceptors

import android.content.Context
import net.bytebuddy.implementation.bind.annotation.Argument
import java.util.concurrent.Executor

internal object ShowSecretsSettingShouldShowTouchInputInterceptor {
  @JvmStatic
  fun intercept(@Argument(0) context: Context): Boolean = false
}

internal object ShowSecretsSettingShouldShowPhysicalInputInterceptor {
  @JvmStatic
  fun intercept(@Argument(0) context: Context): Boolean = false
}

internal object ShowSecretsSettingRegisterCallbackInterceptor {
  @JvmStatic
  fun intercept(
    @Argument(0) context: Context,
    @Argument(1) callback: Runnable
  ): Runnable = Runnable {}

  // The mockable android.jar used by unit tests also exposes a 3-arg overload
  // (Context, Executor, Runnable) that Compose doesn't call directly, but ByteBuddy's
  // name-based matcher intercepts both overloads, so both need a matching delegate.
  @JvmStatic
  fun intercept(
    @Argument(0) context: Context,
    @Argument(1) executor: Executor,
    @Argument(2) callback: Runnable
  ): Runnable = Runnable {}
}
