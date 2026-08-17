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

  @JvmStatic
  fun intercept(
    @Argument(0) context: Context,
    @Argument(1) executor: Executor,
    @Argument(2) callback: Runnable
  ): Runnable = Runnable {}
}
