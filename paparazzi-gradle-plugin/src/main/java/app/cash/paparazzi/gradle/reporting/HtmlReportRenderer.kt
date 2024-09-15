package app.cash.paparazzi.gradle.reporting

import org.gradle.reporting.ReportRenderer
import org.jetbrains.kotlin.com.google.common.io.ByteStreams
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.net.URL

internal class HtmlReportRenderer {

  private val resources: MutableSet<URL> = HashSet()

  fun requireResource(resource: URL) {
    resources.add(resource)
  }

  fun <T> renderer(renderer: ReportRenderer<T, SimpleHtmlWriter>): TextReportRenderer<T> {
    return renderer(TextReportRendererImpl(renderer))
  }

  private fun <T> renderer(renderer: TextReportRendererImpl<T>): TextReportRenderer<T> {
    return object : TextReportRenderer<T>() {
      @Throws(Exception::class)
      override fun writeTo(model: T, out: Writer) {
        renderer.writeTo(model, out)
      }

      override fun writeTo(model: T, file: File) {
        super.writeTo(model, file)
        for (resource in resources) {
          val name: String =
            resource.path.substringAfterLast("/")
          val type: String =
            resource.path.substringAfterLast(".")
          val destFile = File(file.getParentFile(), String.format("%s/%s", type, name))
          if (!destFile.exists()) {
            destFile.getParentFile().mkdirs()
            try {
              val urlConnection = resource.openConnection()
              urlConnection.setUseCaches(false)
              var inputStream: InputStream? = null
              try {
                inputStream = urlConnection.getInputStream()
                var outputStream: OutputStream? = null
                try {
                  outputStream = BufferedOutputStream(
                    FileOutputStream(destFile)
                  )
                  if (inputStream != null) {
                    ByteStreams.copy(inputStream, outputStream)
                  }
                } finally {
                  outputStream?.close()
                }
              } finally {
                inputStream?.close()
              }
            } catch (e: IOException) {
              throw RuntimeException(e)
            }
          }
        }
      }
    }
  }

  class TextReportRendererImpl<T>(private val delegate: ReportRenderer<T, SimpleHtmlWriter>) : TextReportRenderer<T>() {

    @Throws(Exception::class)
    public override fun writeTo(model: T, out: Writer) {
      val htmlWriter = SimpleHtmlWriter(out, "")
      htmlWriter.startElement("html")
      delegate.render(model, htmlWriter)
      htmlWriter.endElement()
    }
  }
}
