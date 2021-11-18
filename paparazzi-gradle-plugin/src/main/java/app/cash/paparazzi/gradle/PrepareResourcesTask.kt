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
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
open class PrepareResourcesTask : DefaultTask() {
  @get:Input
  internal val packageName: Property<String> = project.objects.property(String::class.java)

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal val mergeResourcesOutput: DirectoryProperty = project.objects.directoryProperty()

  @get:Input
  internal val targetSdkVersion: Property<String> = project.objects.property(String::class.java)

  @get:Input
  internal val compileSdkVersion: Property<String> = project.objects.property(String::class.java)

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal val mergeAssetsOutput: DirectoryProperty = project.objects.directoryProperty()

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal val platformDataRoot: DirectoryProperty = project.objects.directoryProperty()

  @get:OutputFile
  internal val paparazziResources: RegularFileProperty = project.objects.fileProperty()

  private val projectDirectory = project.layout.projectDirectory

  @TaskAction
  fun writeResourcesFile() {
    val out = paparazziResources.get().asFile
    out.delete()
    out.bufferedWriter()
      .use {
        it.write(packageName.get())
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
      }
  }

  private fun Directory.relativize(child: Directory): String {
    return asFile.toPath().relativize(child.asFile.toPath()).toFile().invariantSeparatorsPath
  }
}
