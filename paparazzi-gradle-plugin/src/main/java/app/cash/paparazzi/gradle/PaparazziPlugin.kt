package app.cash.paparazzi.gradle

import app.cash.paparazzi.VERSION
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class PaparazziPlugin : Plugin<Project> {
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
      val variantSlug = variant.name.capitalize()

      val writeResourcesTask = project.tasks.register(
          "preparePaparazzi${variantSlug}Resources", PrepareResourcesTask::class.java
      ) {
        // TODO: variant-aware file path
        it.outputs.file("${project.buildDir}/intermediates/paparazzi/resources.txt")

        // Temporary, until AGP provides outputDir as Provider<File>
        it.mergeResourcesProvider = variant.mergeResourcesProvider
        it.outputDir = project.layout.buildDirectory.dir("intermediates/paparazzi/resources.txt")
        it.dependsOn(variant.mergeResourcesProvider)
      }

      project.tasks.named("test${variant.unitTestVariant.name.capitalize()}")
          .configure {
            it.dependsOn(writeResourcesTask)
          }
    }
  }
}