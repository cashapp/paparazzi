package app.cash.paparazzi.internal.interceptors

import android.view.Choreographer
import com.android.internal.lang.System_Delegate

internal object ChoreographerDelegateInterceptor {
  @Suppress("unused")
  @JvmStatic
  public fun intercept(
    choreographer: Choreographer
  ): Long = System_Delegate.nanoTime()
}
