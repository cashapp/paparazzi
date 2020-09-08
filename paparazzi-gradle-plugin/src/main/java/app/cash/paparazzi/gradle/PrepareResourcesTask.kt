/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.gradle

import app.cash.paparazzi.Environment
import app.cash.paparazzi.PaparazziRenderer
import app.cash.paparazzi.dumpEnvironment
import com.android.Version
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.ide.common.symbols.getPackageNameFromManifest
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.VersionNumber
import java.io.File
import javax.inject.Inject

open class PrepareResourcesTask(private val renderer: PaparazziRenderer) : DefaultTask() {
  @Inject
  constructor(): this(renderer = PaparazziRenderer.Library)
  // Replace with @InputDirectory once mergeResourcesProvider.outputDir is of type Provider<File>.

  internal lateinit var variant: BaseVariant
    @Internal get

  internal lateinit var mergeResourcesProvider: TaskProvider<MergeResources>
    @Internal get

  internal var outputResourcesFile: Provider<RegularFile> = project.objects.fileProperty()
    @Internal get

  protected open fun generateEnvironment() = Environment(
          renderer = renderer,
          packageName = project.packageName(),
          resDir = resourcesFolder,
          assetsDir = assetsFolder,
          compileSdkVersion = project.compileSdkVersion().toInt(),
          platformDir = "${project.sdkFolder().absolutePath}/platforms/android-${project.compileSdkVersion()}/",
          reportDir = "${project.buildDir.absolutePath}/reports/paparazzi/${variant.name}",
          goldenImagesFolder = "${project.projectDir.absolutePath}/paparazzi/${variant.name}",
          apkPath = "",
          mergedResourceValueDir = ""
  )

  @TaskAction
  fun writeResourcesFile() {
    outputResourcesFile.get().asFile.apply {
      delete()
      bufferedWriter().use {
        dumpEnvironment(generateEnvironment(), it)
      }
    }
  }

  protected open val assetsFolder: String
    //TODO: This value needs to be taken from the variant
    @Internal get() = "${project.projectDir.absolutePath}/src/main/assets/"


  protected open val resourcesFolder: String
    @Internal get() = mergeResourcesProvider.get().outputDirAsFile().absolutePath

  companion object {
    const val DEFAULT_COMPILE_SDK_VERSION = 28
  }

  open class PrepareAppResourcesTask : PrepareResourcesTask(renderer = PaparazziRenderer.Application) {

    internal lateinit var apkProvider: TaskProvider<PackageAndroidArtifact>
      @Internal get

    override val resourcesFolder: String
      get() = "${outputResourcesFile.get().asFile.parentFile.absolutePath}/apk_dump/res"

    override val assetsFolder: String
      get() = "${outputResourcesFile.get().asFile.parentFile.absolutePath}/apk_dump/assets"

    override fun generateEnvironment(): Environment {
      val outputs = apkProvider.get()
      val apkPath = File(outputs.outputDirectory.asFile.get(), outputs.apkNames.first())
      if (!apkPath.canRead()) {
        throw IllegalStateException("Failed to read ${apkPath.absolutePath}")
      }

      return super.generateEnvironment().copy(
              apkPath = apkPath.absolutePath,
              //I don't know of a better way to get the merged values folder... this is so fragile and requires that building is actually done
              //on the locall machine.
              mergedResourceValueDir = File(mergeResourcesProvider.get().incrementalFolder, "merged.dir").absolutePath.replace("/incremental/package", "/incremental/merge")
      )
    }
  }
}

/**
 * In AGP 3.6 the return type of MergeResources#getOutputDir() was changed from File to
 * DirectoryProperty. Here we use reflection in order to support users that have not upgraded to
 * 3.6 yet.
 */
private fun MergeResources.outputDirAsFile(): File {
  val getOutputDir = this::class.java.getDeclaredMethod("getOutputDir")

  return if (agpVersion() < VersionNumber.parse("3.6.0")) {
    getOutputDir.invoke(this) as File
  } else {
    (getOutputDir.invoke(this) as DirectoryProperty).asFile.get()
  }
}

private fun agpVersion() = VersionNumber.parse(
        try {
          Version.ANDROID_GRADLE_PLUGIN_VERSION
        } catch (e: NoClassDefFoundError) {
          @Suppress("DEPRECATION")
          com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
        }
)

private fun Project.packageName(): String {
  val androidExtension = extensions.getByType(BaseExtension::class.java)
  androidExtension.sourceSets
          .map { it.manifest.srcFile }
          .filter { it.exists() }
          .forEach {
            return getPackageNameFromManifest(it)
          }
  throw IllegalStateException("No source sets available")
}

private fun Project.compileSdkVersion(): String {
  val androidExtension = extensions.getByType(BaseExtension::class.java)
  return androidExtension.compileSdkVersion.substringAfter(
          "android-", PrepareResourcesTask.DEFAULT_COMPILE_SDK_VERSION.toString()
  )
}

private fun Project.sdkFolder(): File {
  val androidExtension = extensions.getByType(BaseExtension::class.java)
  return androidExtension.sdkDirectory
}
