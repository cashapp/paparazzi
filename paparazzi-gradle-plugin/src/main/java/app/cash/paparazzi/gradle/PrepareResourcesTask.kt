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
import com.android.build.gradle.tasks.MergeResources
import com.android.Version
import com.android.ide.common.symbols.getPackageNameFromManifest
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.VersionNumber
import java.io.File

open class PrepareResourcesTask : DefaultTask() {
  // Replace with @InputDirectory once mergeResourcesProvider.outputDir is of type Provider<File>.

  internal lateinit var mergeResourcesProvider: TaskProvider<MergeResources>
    @Internal get

  internal var outputDir: Provider<Directory> = project.objects.directoryProperty()
    @Internal get

  @TaskAction
  fun writeResourcesFile() {
    val out = outputDir.get().asFile
    out.delete()
    out.bufferedWriter()
        .use {
          it.write(project.packageName())
          it.newLine()
          it.write(mergeResourcesProvider.get().outputDirAsFile().path)
          it.newLine()
          it.write(project.compileSdkVersion())
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
        "android-", DEFAULT_COMPILE_SDK_VERSION.toString()
    )
  }

  companion object {
    const val DEFAULT_COMPILE_SDK_VERSION = 28
  }
}
