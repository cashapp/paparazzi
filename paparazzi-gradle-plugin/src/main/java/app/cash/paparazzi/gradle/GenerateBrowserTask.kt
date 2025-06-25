package app.cash.paparazzi.gradle

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Path.Companion.toPath
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.writeText

@CacheableTask
public abstract class GenerateBrowserTask : DefaultTask() {
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  public abstract val snapshotMetadataReports: ConfigurableFileCollection

  @get:Input
  public abstract val projectRoot: Property<String>

  @get:OutputDirectory
  public abstract val output: DirectoryProperty

  @TaskAction
  public fun generateBrowser() {
    val outputDir = output.get().asFile
    outputDir.mkdirs()

    val browserHtmlResource = GenerateBrowserTask::class.java.getResource(BROWSER_HTML_RESOURCE)!!
    val browserHtmlFile = File(outputDir, File(browserHtmlResource.path).name)
    browserHtmlResource.openStream().use { input ->
      browserHtmlFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }

    val browserDataFile = File(outputDir, BROWSER_DATA_JS)
    val all = snapshotMetadataReports.allRelative(projectRoot.get().toPath().toNioPath())
    browserDataFile.writeText("window.all_metadata = ${all.toJson()};")

    logger.quiet(
      """
      Generated snapshot browser [${snapshotMetadataReports.sorted().size} modules, ${all.size} methods] at: ${browserHtmlFile.absolutePath}
      """.trimIndent()
    )
  }

  private fun ConfigurableFileCollection.allRelative(root: Path): List<String> =
    files
      .flatMap { it.listFiles().toList() }
      .map { root.relativize(it.toPath()).toString() }
      .sorted()

  private fun List<String>.toJson() = listOfStringsAdapter.toJson(this)

  private val listOfStringsAdapter: JsonAdapter<List<String>> =
    Moshi.Builder()
      .addLast(KotlinJsonAdapterFactory())
      .build()!!
      .adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
      )
      .indent("  ")
}

private const val BROWSER_DATA_JS = "browser.js"
private const val BROWSER_HTML_RESOURCE = "/app/cash/paparazzi/gradle/browser/index.html"
