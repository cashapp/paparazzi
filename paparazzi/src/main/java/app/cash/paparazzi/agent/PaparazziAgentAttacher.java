package app.cash.paparazzi.agent;

import com.sun.tools.attach.VirtualMachine;

/**
 * A tiny helper main class used for "external attach" when self-attach is disallowed.
 *
 * <p>Runs in a separate JVM process, attaches to the target pid, and loads the agent jar.</p>
 */
public final class PaparazziAgentAttacher {
  private PaparazziAgentAttacher() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected args: <pid> <agentJarPath>");
    }
    String pid = args[0];
    String agentJarPath = args[1];

    VirtualMachine vm = VirtualMachine.attach(pid);
    try {
      vm.loadAgent(agentJarPath);
    } finally {
      vm.detach();
    }
  }
}
