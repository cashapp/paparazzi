package app.cash.paparazzi

public object Flags {
  public const val DEBUG_LINKED_OBJECTS: String = "app.cash.paparazzi.debug.linked.objects"

  /**
   * Maximum number of distinct layoutlib sandboxes (isolated class loaders) a single JVM may
   * create.
   *
   * This is a leak guard, not a cache size. A sandbox that has loaded layoutlib's JNI libraries can
   * never be collected, so each one costs roughly 40 MB for the life of the process. Prefer
   * isolating with `forkEvery` over raising this.
   */
  public const val SANDBOX_LIMIT: String = "app.cash.paparazzi.sandbox.limit"
}
