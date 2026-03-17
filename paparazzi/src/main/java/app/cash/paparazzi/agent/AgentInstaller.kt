package app.cash.paparazzi.agent

import com.sun.tools.attach.VirtualMachine
import java.lang.instrument.Instrumentation
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream

internal object AgentInstaller {
  @Volatile
  private var instrumentation: Instrumentation? = null

  @JvmStatic
  fun agentmain(
    @Suppress("unused") args: String?,
    inst: Instrumentation
  ) {
    instrumentation = inst
  }

  fun install(): Instrumentation {
    instrumentation?.let { return it }

    val pid = ProcessHandle.current().pid().toString()
    val agentJar = createAgentJar()
    try {
      val vm = VirtualMachine.attach(pid)
      try {
        vm.loadAgent(agentJar.toString())
      } finally {
        vm.detach()
      }
    } finally {
      agentJar.toFile().delete()
    }

    return instrumentation
      ?: error("Failed to install agent — Instrumentation not available")
  }

  private fun createAgentJar(): java.nio.file.Path {
    val manifest = Manifest().apply {
      mainAttributes.putValue("Manifest-Version", "1.0")
      mainAttributes.putValue("Agent-Class", "app.cash.paparazzi.agent.AgentInstaller")
      mainAttributes.putValue("Can-Redefine-Classes", "true")
      mainAttributes.putValue("Can-Retransform-Classes", "true")
    }

    val jarPath = createTempFile("paparazzi-agent", ".jar")
    JarOutputStream(jarPath.outputStream(), manifest).close()
    return jarPath
  }
}
