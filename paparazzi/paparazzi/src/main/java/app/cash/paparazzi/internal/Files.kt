package app.cash.paparazzi.internal

import okio.BufferedSink
import okio.buffer
import okio.sink
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun File.writeAtomically(bufferedImage: BufferedImage) {
  val tmpFile = File(parentFile, "$name.tmp")
  ImageIO.write(bufferedImage, "PNG", tmpFile)
  delete()
  tmpFile.renameTo(this)
}

fun File.writeAtomically(writerAction: BufferedSink.() -> Unit) {
  val tmpFile = File(parentFile, "$name.tmp")
  tmpFile.sink()
    .buffer()
    .use { sink ->
      sink.writerAction()
    }
  delete()
  tmpFile.renameTo(this)
}

fun File.toJsonPath(rootDirectory: File): String = relativeTo(rootDirectory).invariantSeparatorsPath
