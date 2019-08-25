package app.cash.paparazzi.gradle

import com.android.build.gradle.tasks.MergeResources
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

open class PrepareResourcesTask : DefaultTask() {
  // Replace with @InputDirectory once mergeResourcesProvider.outputDir is of type Provider<File>.
  internal lateinit var mergeResourcesProvider: TaskProvider<MergeResources>

  @OutputDirectory
  internal var outputDir: Provider<Directory> = project.objects.directoryProperty()

  @TaskAction
  fun writeResourcesFile() {
    val out = outputDir.get()
        .asFile
    out.delete()
    out.writeText(mergeResourcesProvider.get().outputDir.path)
  }
}