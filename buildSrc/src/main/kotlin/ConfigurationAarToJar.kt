package app.cash.paparazzi.gradle

import com.android.build.api.attributes.BuildTypeAttr
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.file.PathTraversalChecker
import org.gradle.internal.impldep.com.google.common.io.Files
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

private val artifactType = Attribute.of("artifactType", String::class.java)

fun configureAarAsJarForConfiguration(project: Project, configurationName: String) {
  println("Setting up $configurationName")
  val testAarsAsJars = project.configurations.create("${configurationName}AarAsJar") {
    isTransitive = true
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(
      BuildTypeAttr.ATTRIBUTE,
      project.objects.named(BuildTypeAttr::class.java, "release")
    )
    attributes.attribute(
      Usage.USAGE_ATTRIBUTE,
      project.objects.named(Usage::class.java, Usage.JAVA_API)
    )
  }
  project.dependencies.registerTransform(IdentityTransform::class.java) {
    from.attribute(artifactType, "jar")
    to.attribute(artifactType, "aarAsJar")
  }

  project.dependencies.registerTransform(ExtractClassesJarTransform::class.java) {
    from.attribute(artifactType, "aar")
    to.attribute(artifactType, "aarAsJar")
  }

  val aarAsJar = testAarsAsJars.incoming.artifactView {
    attributes.attribute(artifactType, "aarAsJar")
  }.files
  project.configurations.getByName(configurationName).dependencies.add(
    project.dependencies.create(aarAsJar)
  )
}

@DisableCachingByDefault
abstract class ExtractClassesJarTransform : TransformAction<TransformParameters.None> {
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  @get:InputArtifact
  abstract val primaryInput: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    ZipFile(primaryInput.get().asFile).use { zip ->
      zip.entries().asSequence()
        .filter { !it.isDirectory }
        .filter {
          // Jars required to be on the classpath
          // https://developer.android.com/studio/projects/android-library#aar-contents
          it.name.endsWith("classes.jar") ||
            it.name.matches("libs/.*.jar".toRegex())
        }
        .forEach {
          val output = outputs.file(it.name)
          println("Copying ${it.name} -> $output")
          zip.getInputStream(it).use { inputStream ->
            java.nio.file.Files.copy(inputStream, output.toPath())
          }
        }
    }
  }
}

@DisableCachingByDefault
abstract class IdentityTransform : TransformAction<TransformParameters.None> {
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  @get:InputArtifact
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(transformOutputs: TransformOutputs) {
    val input = inputArtifact.get().asFile
    when {
      input.isDirectory -> transformOutputs.dir(input)
      input.isFile -> transformOutputs.file(input)
      else -> throw IllegalArgumentException(
        "File/directory does not exist: ${input.absolutePath}"
      )
    }
  }
}
