package app.cash.paparazzi.gradle.reporting

import org.gradle.api.UncheckedIOException
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer

/**
 * Custom TextReportRenderer based on Gradle's TextReportRenderer
 */
internal abstract class TextReportRenderer<T> {

  /**
   * Renders the report for the given model to a writer.
   */
  @Throws(Exception::class)
  protected abstract fun writeTo(model: T, out: Writer)

  /**
   * Renders the report for the given model to a file.
   */
  open fun writeTo(model: T, file: File) {
    try {
      val parentFile: File = file.getParentFile()
      if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
        throw IOException(String.format("Unable to create directory '%s'", parentFile))
      } else {
        val writer =
          BufferedWriter(OutputStreamWriter(FileOutputStream(file), "utf-8"))
        writer.use {
          writeTo(model, it)
        }
      }
    } catch (var8: java.lang.Exception) {
      throw UncheckedIOException(
        String.format(
          "Could not write to file '%s'.",
          file
        ),
        var8
      )
    }
  }
}
