package app.cash.paparazzi.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

@CacheableTask
public abstract class GeneratePreviewTestFileTask : DefaultTask() {
  @get:Input
  public abstract val namespace: Property<String>

  @get:Optional
  @get:SkipWhenEmpty
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val paparazziPreviewsFile: RegularFileProperty

  @get:OutputDirectory
  public abstract val previewTestOutputDir: DirectoryProperty

  @get:OutputFile
  public abstract val previewTestOutputFile: RegularFileProperty

  @TaskAction
  public fun createFile() {
    val previewDataFile = paparazziPreviewsFile.asFile.get()
    val previewTestFile = previewTestOutputFile.asFile.get()

    if (!previewDataFile.exists()) {
      logger.warn("Preview data file not found: $previewDataFile")
      return
    }

    previewTestFile.writeText(previewTestSource(namespace.get()))
  }
}
