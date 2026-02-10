package app.cash.paparazzi.agent

import java.io.Closeable
import java.io.File

/**
 * Attachment helpers. Implemented with reflection to avoid hard dependency failures on runtimes
 * without the attach module.
 */
internal object PaparazziAttach {
  fun attachAndLoadAgent(pid: String, agentJar: File): Closeable {
    val vmClass = Class.forName("com.sun.tools.attach.VirtualMachine")
    val attach = vmClass.getMethod("attach", String::class.java)
    val loadAgent = vmClass.getMethod("loadAgent", String::class.java)
    val detach = vmClass.getMethod("detach")

    val vm = attach.invoke(null, pid)
    try {
      loadAgent.invoke(vm, agentJar.absolutePath)
    } finally {
      detach.invoke(vm)
    }
    return Closeable { detach.invoke(vm) }
  }

  fun attachAndLoadAgentInSeparateProcess(pid: String, agentJar: File): Closeable {
    val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
    val classpath = codeSourcePath()

    val process = ProcessBuilder(
      javaBin,
      "--add-modules",
      "jdk.attach",
      "-cp",
      classpath,
      PaparazziAgentAttacher::class.java.name,
      pid,
      agentJar.absolutePath
    )
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exit = process.waitFor()
    check(exit == 0) { "External attach helper failed with exit code $exit\n$output" }
    return Closeable { process.destroy() }
  }

  private fun codeSourcePath(): String {
    val location = PaparazziAttach::class.java.protectionDomain.codeSource.location
      ?: throw IllegalStateException("Unable to resolve codeSource for Paparazzi")
    val codeSourceFile = File(location.toURI())
    if (!codeSourceFile.isDirectory) return codeSourceFile.absolutePath

    // When running from Gradle, Kotlin and Java classes may be in different output directories.
    // Ensure the helper process can see both (at minimum) so it can find the attacher main class.
    val entries = mutableListOf(codeSourceFile.absolutePath)

    val buildDir = findParentNamed(codeSourceFile, "build")
    if (buildDir != null) {
      val javaClasses = File(buildDir, "classes/java/main")
      if (javaClasses.exists()) entries += javaClasses.absolutePath
    }

    return entries.joinToString(File.pathSeparator)
  }

  private fun findParentNamed(start: File, name: String): File? {
    var cur: File? = start
    while (cur != null) {
      if (cur.name == name) return cur
      cur = cur.parentFile
    }
    return null
  }
}
