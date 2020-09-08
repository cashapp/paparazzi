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

import app.cash.paparazzi.PAPARAZZI_RESOURCES_DETAILS_FILE_KEY
import app.cash.paparazzi.VERSION
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.api.TestedVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import java.util.Locale

@OptIn(ExperimentalStdlibApi::class)
class PaparazziPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val isAndroidLibrary = project.plugins.hasPlugin("com.android.library")
    val isAndroidApplication = project.plugins.hasPlugin("com.android.application")

    if (!isAndroidApplication && !isAndroidLibrary) {
      throw IllegalArgumentException("The Android Gradle library/application plugin must be applied before the Paparazzi plugin.")
    }

    project.configurations.getByName("testImplementation").dependencies.add(
        project.dependencies.create("app.cash.paparazzi:paparazzi:$VERSION")
    )

    if (isAndroidLibrary) {
      project.extensions.getByType(LibraryExtension::class.java)
              .libraryVariants
              .all { variant ->
                setupResourcesFunction(
                        project.tasks.register("preparePaparazzi${variant.name.capitalize(Locale.US)}Resources", PrepareResourcesTask::class.java),
                        project, variant, variant)
              }
    } else {
      project.extensions.getByType(AppExtension::class.java)
              .applicationVariants
              .all { variant ->
                val taskProvider = project.tasks.register("preparePaparazzi${variant.name.capitalize(Locale.US)}AppResources", PrepareResourcesTask.PrepareAppResourcesTask::class.java)
                setupResourcesFunction(taskProvider, project, variant, variant)
                taskProvider.configure {
                  it.dependsOn(variant.packageApplicationProvider)
                  it.apkProvider = variant.packageApplicationProvider
                }
              }

    }
  }

  private fun setupResourcesFunction(writeResourcesTask: TaskProvider<out PrepareResourcesTask>, project: Project, variant: BaseVariant, testVariant: TestedVariant) {
    val paparazziResourcesDetailsFile = "intermediates/paparazzi/${variant.name}/resources.txt"
    writeResourcesTask.configure {
      it.outputs.file("${project.buildDir}/${paparazziResourcesDetailsFile}")

      // Temporary, until AGP provides outputDir as Provider<File>
      it.dependsOn(variant.mergeResourcesProvider)
      it.mergeResourcesProvider = variant.mergeResourcesProvider
      it.outputResourcesFile = project.layout.buildDirectory.file(paparazziResourcesDetailsFile)
      it.variant = testVariant.unitTestVariant
      it.overwriteGoldenMedia = project.rootProject.hasProperty("PAPARAZZI_OVERWRITE_GOLDEN")
    }

    val testVariantSlug = testVariant.unitTestVariant.name.capitalize(Locale.US)

    project.plugins.withType(JavaBasePlugin::class.java) {
      project.tasks.named("compile${testVariantSlug}JavaWithJavac")
              .configure { it.dependsOn(writeResourcesTask) }
    }

    project.plugins.withType(KotlinBasePluginWrapper::class.java) {
      project.tasks.named("compile${testVariantSlug}Kotlin")
              .configure { it.dependsOn(writeResourcesTask) }
    }

    project.tasks.named("test${testVariantSlug}", Test::class.java).configure {
      //TODO: Ensure the golden-images folder is also marked as an input to the task. In case golden-images are changed the tests should re-run.
      it.systemProperties[PAPARAZZI_RESOURCES_DETAILS_FILE_KEY] = "${project.buildDir.absolutePath}/${paparazziResourcesDetailsFile}"
    }
  }
}
