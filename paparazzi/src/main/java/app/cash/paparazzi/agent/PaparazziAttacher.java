package app.cash.paparazzi.agent;

import com.sun.tools.attach.VirtualMachine;

/** Runs in a separate JVM because HotSpot disables self-attachment by default. */
final class PaparazziAttacher {
  public static void main(String[] arguments) throws Exception {
    VirtualMachine vm = VirtualMachine.attach(arguments[0]);
    try {
      vm.loadAgent(arguments[1]);
    } finally {
      vm.detach();
    }
  }
}
