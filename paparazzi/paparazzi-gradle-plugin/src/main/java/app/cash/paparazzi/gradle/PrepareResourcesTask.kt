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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class PrepareResourcesTask : DefaultTask() {
  @get:Input
  abstract val packageName: Property<String>

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val mergeResourcesOutput: DirectoryProperty

  @get:Input
  abstract val targetSdkVersion: Property<String>

  @get:Input
  abstract val compileSdkVersion: Property<String>

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val mergeAssetsOutput: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val platformDataRoot: DirectoryProperty

  @get:Input
  abstract val nonTransitiveRClassEnabled: Property<Boolean>

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val artifactFiles: ConfigurableFileCollection

  @get:OutputFile
  abstract val paparazziResources: RegularFileProperty

  private val projectDirectory = project.layout.projectDirectory

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
        it.write(projectDirectory.relativize(mergeResourcesOutput.get()))
        it.newLine()
        it.write(targetSdkVersion.get())
        it.newLine()
        // Use compileSdkVersion for system framework resources.
        it.write("platforms/android-${compileSdkVersion.get()}/")
        it.newLine()
        it.write(projectDirectory.relativize(mergeAssetsOutput.get()))
        it.newLine()
        it.write(platformDataRoot.get().asFile.invariantSeparatorsPath)
        it.newLine()
        it.write(resourcePackageNames)
        it.newLine()
      }
  }

  private fun Directory.relativize(child: Directory): String {
    return asFile.toPath().relativize(child.asFile.toPath()).toFile().invariantSeparatorsPath
  }
}
