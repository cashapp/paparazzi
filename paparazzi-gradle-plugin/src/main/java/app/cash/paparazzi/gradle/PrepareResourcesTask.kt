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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariant
import com.android.ide.common.symbols.getPackageNameFromManifest
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
open class PrepareResourcesTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal val mergeResourcesOutput: DirectoryProperty = project.objects.directoryProperty()

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  internal val mergeAssetsOutput: DirectoryProperty = project.objects.directoryProperty()

  @get:OutputFile
  internal val paparazziResources: RegularFileProperty = project.objects.fileProperty()


  @TaskAction
  fun writeResourcesFile() {
    val out = paparazziResources.get().asFile
    out.delete()
    out.bufferedWriter()
        .use {
          it.write(project.packageName())
          it.newLine()
          it.write(mergeResourcesOutput.get().asFile.path)
          it.newLine()
          it.write(project.targetSdkVersion())
          it.newLine()
          // will use the compile version for system framework resources
          it.write("${project.sdkFolder().absolutePath}/platforms/android-${project.compileSdkVersion()}/")
          it.newLine()
          it.write(mergeAssetsOutput.get().asFile.path)
          it.newLine()
        }
  }

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
        "android-", DEFAULT_COMPILE_SDK_VERSION.toString()
    )
  }

  private fun Project.targetSdkVersion(): String {
    val androidExtension = extensions.getByType(BaseExtension::class.java)
    return androidExtension.defaultConfig.targetSdkVersion?.apiLevel?.toString()
            ?: DEFAULT_COMPILE_SDK_VERSION.toString()
  }

  private fun Project.sdkFolder(): File {
    val androidExtension = extensions.getByType(BaseExtension::class.java)
    return androidExtension.sdkDirectory!!
  }

  companion object {
    const val DEFAULT_COMPILE_SDK_VERSION = 29
  }
}
