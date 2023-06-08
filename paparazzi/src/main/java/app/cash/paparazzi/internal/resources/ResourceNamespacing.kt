package app.cash.paparazzi.internal.resources

enum class ResourceNamespacing {
  /**
   * Resources are not namespaced.
   */
  DISABLED,

  /**
   * Resources must be namespaced.
   */
  REQUIRED
}
