package app.cash.paparazzi.gradle

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Date

@CacheableTask
public abstract class ReportPaparazziTask : SourceTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val paparazziReportTempDirectory: DirectoryProperty

  @get:OutputDirectory
  public abstract val paparazziReportDirectory: DirectoryProperty

  private val runsDirectory: Provider<Directory>
    get() = paparazziReportTempDirectory.dir("runs")

  @TaskAction
  public fun createReport() {
    val reportDir = paparazziReportDirectory.get().asFile
    val tempDir = paparazziReportTempDirectory.asFile.get()
    tempDir.copyRecursively(reportDir, overwrite = true)

    if (!runsDirectory.get().asFile.exists()) {
      logger.log(LogLevel.LIFECYCLE, "No runs to report.")
      return
    }

    writeStaticFiles()
    writeIndexJs()

    val uri = reportDir.toPath().resolve("index.html").toUri()
    logger.log(LogLevel.LIFECYCLE, "See the Paparazzi report at: $uri")
  }

  private fun writeStaticFiles() {
    for (staticFile in listOf("index.html", "paparazzi.js")) {
      File(paparazziReportDirectory.get().asFile, staticFile).writeAtomically {
        writeAll(ReportPaparazziTask::class.java.classLoader.getResourceAsStream(staticFile).source())
      }
    }
  }

  /**
   * Emits the all runs index, which reads like JSON with an executable header.
   *
   * ```
   * window.all_runs = [
   *   "20190319153912aaab",
   *   "20190319153917bcfe"
   * ];
   * ```
   */
  private fun writeIndexJs() {
    val runNames = mutableListOf<String>()
    val runs = runsDirectory.get().asFile.list()!!.sorted()
    for (run in runs) {
      if (run.endsWith(".js")) {
        runNames += run.substring(0, run.length - 3)
      }
    }

    File(paparazziReportDirectory.get().asFile, "index.js").writeAtomically {
      writeUtf8("window.all_runs = ")

      Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()!!
        .adapter<List<String>>(
          Types.newParameterizedType(List::class.java, String::class.java)
        )
        .indent("  ")
        .toJson(this, runNames)

      writeUtf8(";")
    }
  }

  private fun File.writeAtomically(writerAction: BufferedSink.() -> Unit) {
    val tmpFile = File(parentFile, "$name.tmp")
    tmpFile.sink()
      .buffer()
      .use { sink ->
        sink.writerAction()
      }
    delete()
    tmpFile.renameTo(this)
  }
}
