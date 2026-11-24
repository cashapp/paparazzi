package app.cash.paparazzi.agent

import java.io.File
import java.lang.instrument.Instrumentation
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/** Installs a small JDK-only agent without requiring changes to the test JVM's launch arguments. */
internal object AgentInstaller {
  private const val AGENT_CLASS = "app.cash.paparazzi.agent.PaparazziAgent"
  private const val ATTACHER_CLASS = "app.cash.paparazzi.agent.PaparazziAttacher"
  private var instrumentation: Instrumentation? = null

  @Synchronized
  fun install(): Instrumentation {
    instrumentation?.let { return it }
    installedAgent()?.let {
      instrumentation = it
      return it
    }

    val agentJar = File.createTempFile("paparazzi-agent-", ".jar").apply { deleteOnExit() }
    val output = File.createTempFile("paparazzi-attach-", ".log")
    try {
      writeAgentJar(agentJar)
      val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
      val java = File(System.getProperty("java.home"), "bin/$executable")
      val process = ProcessBuilder(
        java.absolutePath,
        "--add-modules=jdk.attach",
        "-jar",
        agentJar.absolutePath,
        ProcessHandle.current().pid().toString(),
        agentJar.absolutePath
      )
        .redirectErrorStream(true)
        .redirectOutput(output)
        .start()
      try {
        check(process.waitFor(30, TimeUnit.SECONDS)) {
          "Timed out attaching Paparazzi's instrumentation agent. ${output.readText()}"
        }
        check(process.exitValue() == 0) {
          "Could not attach Paparazzi's instrumentation agent using $java. ${output.readText()}"
        }
      } finally {
        if (process.isAlive) process.destroyForcibly()
      }
      return checkNotNull(installedAgent()) {
        "Paparazzi's agent attached but did not publish its Instrumentation instance."
      }.also { instrumentation = it }
    } finally {
      output.delete()
      // The system loader can keep the agent JAR open on Windows; deleteOnExit covers that case.
      agentJar.delete()
    }
  }

  private fun installedAgent(): Instrumentation? {
    val agent = try {
      Class.forName(AGENT_CLASS, false, ClassLoader.getSystemClassLoader())
    } catch (_: ClassNotFoundException) {
      return null
    }
    // Read the system loader's copy, which can differ from the Gradle test loader's copy.
    return agent.getDeclaredMethod("getInstrumentation").apply { isAccessible = true }
      .invoke(null) as Instrumentation?
  }

  private fun writeAgentJar(file: File) {
    val manifest = Manifest().apply {
      mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      mainAttributes[Attributes.Name.MAIN_CLASS] = ATTACHER_CLASS
      mainAttributes.putValue("Agent-Class", AGENT_CLASS)
      mainAttributes.putValue("Premain-Class", AGENT_CLASS)
      mainAttributes.putValue("Can-Redefine-Classes", "true")
      mainAttributes.putValue("Can-Retransform-Classes", "true")
    }
    JarOutputStream(file.outputStream(), manifest).use { jar ->
      for (name in listOf(AGENT_CLASS, ATTACHER_CLASS)) {
        val path = name.replace('.', '/') + ".class"
        val bytes = checkNotNull(AgentInstaller::class.java.getResourceAsStream("/$path")) {
          "Missing Paparazzi agent class: $path"
        }.use { it.readBytes() }
        jar.putNextEntry(JarEntry(path))
        jar.write(bytes)
        jar.closeEntry()
      }
    }
  }
}
