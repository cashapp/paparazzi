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

import app.cash.paparazzi.gradle.utils.relativize
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class PrepareResourcesTask : DefaultTask() {
  @get:Input
  abstract val packageName: Property<String>

  @Deprecated("legacy resource loading, to be removed in a future release")
  @get:Input
  abstract val mergeResourcesOutputDir: Property<String>

  @get:Input
  abstract val targetSdkVersion: Property<String>

  @get:Input
  abstract val compileSdkVersion: Property<String>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val projectResourceDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val moduleResourceDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val aarExplodedDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val projectAssetDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val aarAssetDirs: ConfigurableFileCollection

  @Deprecated("legacy asset loading, to be removed in a future release")
  @get:Input
  abstract val mergeAssetsOutputDir: Property<String>

  @get:Input
  abstract val nonTransitiveRClassEnabled: Property<Boolean>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val artifactFiles: ConfigurableFileCollection

  @get:OutputFile
  abstract val paparazziResources: RegularFileProperty

  private val projectDirectory = project.layout.projectDirectory

  private val gradleUserHomeDirectory = projectDirectory.dir(project.gradle.gradleUserHomeDir.path)

  @TaskAction
  // TODO: figure out why this can't be removed as of Kotlin 1.6+
  @OptIn(ExperimentalStdlibApi::class)
  fun writeResourcesFile() {
    val out = paparazziResources.get().asFile
    out.delete()

    val mainPackage = packageName.get()
    val resourcePackageNames = if (nonTransitiveRClassEnabled.get()) {
      buildList {
        add(mainPackage)
        artifactFiles.files.forEach { file ->
          add(file.useLines { lines -> lines.first() })
        }
      }
    } else {
      listOf(mainPackage)
    }

    val config = Config(
      mainPackage = mainPackage,
      mergeResourcesOutputDir = mergeResourcesOutputDir.get(),
      targetSdkVersion = targetSdkVersion.get(),
      // Use compileSdkVersion for system framework resources.
      platformDir = "platforms/android-${compileSdkVersion.get()}/",
      mergeAssetsOutputDir = mergeAssetsOutputDir.get(),
      resourcePackageNames = resourcePackageNames,
      projectResourceDirs = projectResourceDirs.relativize(projectDirectory),
      moduleResourceDirs = moduleResourceDirs.relativize(projectDirectory),
      aarExplodedDirs = aarExplodedDirs.relativize(gradleUserHomeDirectory),
      projectAssetDirs = projectAssetDirs.relativize(projectDirectory),
      aarAssetDirs = aarAssetDirs.relativize(gradleUserHomeDirectory)
    )
    val moshi = Moshi.Builder().build()!!
    val json = moshi.adapter(Config::class.java).indent("  ").toJson(config)
    out.writeText(json)
  }

  @JsonClass(generateAdapter = true)
  data class Config(
    val mainPackage: String,
    val mergeResourcesOutputDir: String,
    val targetSdkVersion: String,
    val platformDir: String,
    val mergeAssetsOutputDir: String,
    val resourcePackageNames: List<String>,
    val projectResourceDirs: List<String>,
    val moduleResourceDirs: List<String>,
    val aarExplodedDirs: List<String>,
    val projectAssetDirs: List<String>,
    val aarAssetDirs: List<String>
  )
}
