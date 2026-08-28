package app.cash.paparazzi

public object Flags {
  public const val DEBUG_LINKED_OBJECTS: String = "app.cash.paparazzi.debug.linked.objects"

  /**
   * Maximum number of live layoutlib sandboxes (isolated class loaders) kept cached.
   *
   * Each sandbox holds a private copy of layoutlib's JNI libraries, so raising this increases
   * native memory and metaspace roughly linearly.
   */
  public const val SANDBOX_MAX_SIZE: String = "app.cash.paparazzi.sandbox.max.size"
}
