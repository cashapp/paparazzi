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

import app.cash.paparazzi.gradle.utils.joinFiles
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

  @get:Input
  abstract val mergeResourcesOutputDir: Property<String>

  @get:Input
  abstract val targetSdkVersion: Property<String>

  @get:Input
  abstract val compileSdkVersion: Property<String>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val projectResourceDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val moduleResourceDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val aarExplodedDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val projectAssetDirs: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val aarAssetDirs: ConfigurableFileCollection

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
      }.joinToString(",")
    } else {
      mainPackage
    }

    out.bufferedWriter()
      .use {
        it.write(mainPackage)
        it.newLine()
        it.write(mergeResourcesOutputDir.get())
        it.newLine()
        it.write(targetSdkVersion.get())
        it.newLine()
        // Use compileSdkVersion for system framework resources.
        it.write("platforms/android-${compileSdkVersion.get()}/")
        it.newLine()
        it.write(mergeAssetsOutputDir.get())
        it.newLine()
        it.write(resourcePackageNames)
        it.newLine()
        it.write(projectResourceDirs.joinFiles(projectDirectory))
        it.newLine()
        it.write(moduleResourceDirs.joinFiles(projectDirectory))
        it.newLine()
        it.write(aarExplodedDirs.joinFiles(gradleUserHomeDirectory))
        it.newLine()
        it.write(projectAssetDirs.joinFiles(projectDirectory))
        it.newLine()
        it.write(aarAssetDirs.joinFiles(gradleUserHomeDirectory))
        it.newLine()
      }
  }
}
