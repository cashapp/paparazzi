package app.cash.paparazzi.agent
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.lang.instrument.Instrumentation
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * A minimal JVM agent used to obtain [Instrumentation] without ByteBuddy.
 *
 * This agent is loaded (attached) at runtime and stores the provided [Instrumentation] in a static field
 * that the main Paparazzi runtime can access.
 */
internal object PaparazziAsmAgent {
  private var closable: Closeable? = null

  @JvmStatic
  fun premain(args: String?, inst: Instrumentation) {
    publishInstrumentation(inst)
  }

  @JvmStatic
  fun agentmain(args: String?, inst: Instrumentation) {
    publishInstrumentation(inst)
  }

  fun getInstrumentationOrNull(): Instrumentation? = PaparazziInstrumentationHolder.instrumentation

  fun install(): Instrumentation {
    PaparazziInstrumentationHolder.instrumentation?.let { return it }
    closable?.close()
    closable = null

    val agentJar = buildTempAgentJar()
    val pid = ProcessHandle.current().pid().toString()

    // Attempt self-attach first; if that fails (common on modern JDKs), fall back to external attach.
    closable = try {
      PaparazziAttach.attachAndLoadAgent(pid = pid, agentJar = agentJar)
    } catch (_: Throwable) {
      PaparazziAttach.attachAndLoadAgentInSeparateProcess(pid = pid, agentJar = agentJar)
    }

    // Wait briefly for agentmain/premain to populate instrumentation.
    val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (PaparazziInstrumentationHolder.instrumentation == null && System.nanoTime() < deadlineNanos) {
      Thread.sleep(10)
    }

    return PaparazziInstrumentationHolder.instrumentation ?: throw IllegalStateException(
      "Failed to acquire Instrumentation. Ensure you're running on a full JDK with the 'jdk.attach' module available."
    )
  }

  fun uninstall() {
    closable?.close()
  }

  private fun buildTempAgentJar(): File {
    // Avoid java.nio.file APIs to keep Android API lint happy; this runs on the JVM.
    val tmpDir = createTempDir(prefix = "paparazzi-agent-")
    tmpDir.deleteOnExit()
    val jarFile = File(tmpDir, "paparazzi-asm-agent.jar")
    jarFile.deleteOnExit()

    val manifest = Manifest().apply {
      mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      mainAttributes.putValue("Agent-Class", PaparazziAsmAgent::class.java.name)
      mainAttributes.putValue("Can-Redefine-Classes", "true")
      mainAttributes.putValue("Can-Retransform-Classes", "true")
    }

    JarOutputStream(FileOutputStream(jarFile), manifest).use { jos ->
      writeClassToJar(jos, PaparazziAsmAgent::class.java)
      // Not strictly required, but keeps the agent jar self-contained.
      writeClassToJar(jos, PaparazziInstrumentationHolder::class.java)
    }
    return jarFile
  }

  private fun writeClassToJar(jos: JarOutputStream, clazz: Class<*>) {
    val internalName = clazz.name.replace('.', '/') + ".class"
    val bytes = requireNotNull(clazz.classLoader.getResourceAsStream(internalName)) {
      "Unable to locate class bytes for ${clazz.name}"
    }.use { it.readBytes() }

    jos.putNextEntry(JarEntry(internalName))
    jos.write(bytes)
    jos.closeEntry()
  }

  private fun publishInstrumentation(inst: Instrumentation) {
    // Publish into the current classloader's holder.
    PaparazziInstrumentationHolder.instrumentation = inst

    // Best-effort: publish into the holder loaded by the system classloader (which the main runtime can see),
    // even if the agent class itself was loaded from a different jar/classloader.
    runCatching {
      val sys = ClassLoader.getSystemClassLoader()
      val holder = Class.forName(
        "app.cash.paparazzi.agent.PaparazziInstrumentationHolder",
        true,
        sys
      )
      val field = holder.getDeclaredField("instrumentation")
      field.isAccessible = true
      field.set(null, inst)
    }
  }
}
