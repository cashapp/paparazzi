package app.cash.paparazzi.agent

import java.lang.instrument.Instrumentation

/**
 * Shared holder for JVM [Instrumentation].
 *
 * This exists to avoid classloader issues when the agent class is loaded from a different jar/classloader
 * than the main Paparazzi runtime.
 */
internal object PaparazziInstrumentationHolder {
  @JvmField
  @Volatile
  var instrumentation: Instrumentation? = null
}
