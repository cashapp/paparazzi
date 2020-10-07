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

import app.cash.paparazzi.VERSION
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import java.util.Locale

@Suppress("unused")
class PaparazziPlugin : Plugin<Project> {
  @OptIn(ExperimentalStdlibApi::class)
  override fun apply(project: Project) {
    require(project.plugins.hasPlugin("com.android.library")) {
      "The Android Gradle library plugin must be applied before the Paparazzi plugin."
    }

    project.configurations.getByName("testImplementation").dependencies.add(
        project.dependencies.create("app.cash.paparazzi:paparazzi:$VERSION")
    )

    val variants = project.extensions.getByType(LibraryExtension::class.java)
        .libraryVariants
    variants.all { variant ->
      val variantSlug = variant.name.capitalize(Locale.US)

      val reportOutputFolder = project.layout.buildDirectory.dir("reports/paparazzi/$variantSlug")
      val writeResourcesTask = project.tasks.register(
          "preparePaparazzi${variantSlug}Resources", PrepareResourcesTask::class.java
      ) { task ->
        task.mergeResourcesOutput.set(variant.mergeResourcesProvider.flatMap { it.outputDir })
        task.mergeAssetsOutput.set(variant.mergeAssetsProvider.flatMap { it.outputDir })
        task.paparazziResources.set(project.layout.buildDirectory.file("intermediates/paparazzi/${variant.name}/resources.txt"))
        task.paparazziReportOutput = reportOutputFolder.get().asFile.absolutePath
      }

      val testVariantSlug = variant.unitTestVariant.name.capitalize(Locale.US)

      project.plugins.withType(JavaBasePlugin::class.java) {
        project.tasks.named("compile${testVariantSlug}JavaWithJavac")
            .configure { it.dependsOn(writeResourcesTask) }
      }

      project.plugins.withType(KotlinBasePluginWrapper::class.java) {
        project.tasks.named("compile${testVariantSlug}Kotlin")
            .configure { it.dependsOn(writeResourcesTask) }
      }

      val recordTaskProvider = project.tasks.register("recordPaparazzi${variantSlug}")
      val verifyTaskProvider = project.tasks.register("verifyPaparazzi${variantSlug}")

      val testTaskProvider = project.tasks.named("test${testVariantSlug}", Test::class.java) { test ->
        test.systemProperties["paparazzi.test.resources"] =
            writeResourcesTask.flatMap { it.paparazziResources.asFile }.get().path
        //We're adding the report folder to the test-task's outputs to ensure
        //it is stored in Gradle's cache.
        test.outputs.dir(reportOutputFolder)
        test.doFirst {
          test.systemProperties["paparazzi.test.record"] =
              project.gradle.taskGraph.hasTask(recordTaskProvider.get())
          test.systemProperties["paparazzi.test.verify"] =
              project.gradle.taskGraph.hasTask(verifyTaskProvider.get())
        }
      }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }

      testTaskProvider.configure {
        it.doLast {
          val uri = reportOutputFolder.get().asFile.toPath().resolve("index.html").toUri()
          project.logger.log(LIFECYCLE, "See the Paparazzi report at: $uri")
        }
      }
    }
  }
}