package app.cash.paparazzi.gradle.instrumentation

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Rewrites `androidx.core.content.res.ResourcesCompat` on a test runtime classpath.
 *
 * AGP applies [ResourcesCompatVisitorFactory] to the classpath of host-test components it owns.
 * Paparazzi's own source set has no such component, so the same fix is applied here as an artifact
 * transform. Rewriting the artifact in place is what makes this equivalent to AGP's behaviour;
 * shadowing the class with an extra jar earlier on the classpath is not reliable.
 *
 * Jars that do not contain the class are passed through untouched.
 */
@CacheableTransform
internal abstract class ResourcesCompatTransformAction : TransformAction<TransformParameters.None> {
  @get:Classpath
  @get:InputArtifact
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val input = inputArtifact.get().asFile
    val entryName =
      ResourcesCompatVisitorFactory.RESOURCES_COMPAT_CLASS_NAME.replace('.', '/') + ".class"

    if (!input.isFile || !input.name.endsWith(".jar")) {
      outputs.file(input)
      return
    }

    val containsTarget = JarFile(input).use { it.getJarEntry(entryName) != null }
    if (!containsTarget) {
      outputs.file(input)
      return
    }

    val output = outputs.file("${input.nameWithoutExtension}-paparazzi.jar")
    JarFile(input).use { jar ->
      JarOutputStream(output.outputStream().buffered()).use { out ->
        jar.entries().asSequence().forEach { entry ->
          if (entry.isDirectory) return@forEach
          val bytes = jar.getInputStream(entry).use { it.readBytes() }
          val outputBytes = if (entry.name == entryName) instrument(bytes) else bytes
          // Recreate the entry so sizes/compression are recalculated for rewritten classes.
          out.putNextEntry(ZipEntry(entry.name))
          out.write(outputBytes)
          out.closeEntry()
        }
      }
    }
  }

  private fun instrument(bytes: ByteArray): ByteArray {
    val reader = ClassReader(bytes)
    val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)
    reader.accept(ResourcesCompatVisitorFactory.ResourcesCompatTransform(writer), 0)
    return writer.toByteArray()
  }
}
