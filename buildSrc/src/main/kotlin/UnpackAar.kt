package app.cash.paparazzi.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.internal.file.PathTraversalChecker
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

// Attributes used by aar dependencies
private val artifactType = Attribute.of("artifactType", String::class.java)
private val unpackedAar = Attribute.of("unpackedAar", Boolean::class.javaObjectType)

fun Project.configureAar() =
  configurations.configureEach {
    // Causes issue with spotless
    if (name.contains("spotless", ignoreCase = true)) return@configureEach

    afterEvaluate {
      if (isCanBeResolved && !isCanBeConsumed) {
        attributes.attribute(unpackedAar, true) // request all AARs to be unpacked
      }
    }
  }

fun DependencyHandler.configureAarUnpacking() {
  attributesSchema {
    attribute(unpackedAar)
    attribute(KotlinPlatformType.attribute) {
      compatibilityRules.add(AndroidJvmAndJvmCompatible::class.java)
    }
    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE) {
      compatibilityRules.add(AarAndJarCompatible::class.java)
    }
  }

  artifactTypes {
    create(AAR) {
      attributes.attribute(unpackedAar, false)
    }
  }

  registerTransform(UnpackAar::class.java) {
    from
      .attribute(unpackedAar, false)
      .attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
      .attribute(artifactType, AAR)
    to
      .attribute(unpackedAar, true)
      .attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
      .attribute(artifactType, LibraryElements.JAR)
  }
}

@Suppress("UnstableApiUsage")
abstract class UnpackAar : TransformAction<TransformParameters.None> {

  @get:InputArtifact
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    ZipFile(inputArtifact.get().asFile).use { zip ->
      zip.entries().asSequence()
        .filter { !it.isDirectory }
        .filter {
          // Jars required to be on the classpath
          // https://developer.android.com/studio/projects/android-library#aar-contents
          it.name.endsWith("classes.jar") ||
            it.name.matches("libs/.*.jar".toRegex())
        }
        .forEach {
          val output = outputs.file(PathTraversalChecker.safePathName(it.name))
          zip.unzip(it, output)
        }
    }
  }
}

private fun ZipFile.unzip(entry: ZipEntry, output: File) {
  getInputStream(entry).use {
    Files.copy(it, output.toPath())
  }
}

private val AAR = "aar"

