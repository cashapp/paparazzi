package app.cash.paparazzi.internal.interceptors

internal object AnimationConstantsInterceptor {
  @JvmStatic
  fun intercept(): Long {
    println("Intercepting AnimationConstants")
    return 0L
  }
}
