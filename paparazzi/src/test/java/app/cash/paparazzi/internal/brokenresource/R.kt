package app.cash.paparazzi.internal.brokenresource

object R {
  init {
    error("Broken R class initializer")
  }
}
