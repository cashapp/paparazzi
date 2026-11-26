package app.cash.paparazzi.agent;

import java.lang.instrument.Instrumentation;

/** JDK-only entry point: agents are loaded by the system class loader, outside the test loader. */
final class PaparazziAgent {
  private static volatile Instrumentation instrumentation;

  public static void premain(String arguments, Instrumentation value) {
    instrumentation = value;
  }

  public static void agentmain(String arguments, Instrumentation value) {
    instrumentation = value;
  }

  public static Instrumentation getInstrumentation() {
    return instrumentation;
  }
}
