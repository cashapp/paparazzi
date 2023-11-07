package app.cash.paparazzi.files

import okio.BufferedSink
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import okio.sink
import org.jcodec.api.awt.AWTSequenceEncoder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Returns the hash of the image. */
fun writeImage(image: BufferedImage, imagesDirectory: File): String {
  val hash = hash(image)
  val file = File(imagesDirectory, "$hash.png")
  if (!file.exists()) {
    file.writeAtomically(image)
  }
  return hash
}

fun writeVideo(
  frameHashes: List<String>,
  fps: Int,
  imagesDirectory: File,
  videosDirectory: File
): String {
  val hash = hash(frameHashes)
  val file = File(videosDirectory, "$hash.mov")
  if (!file.exists()) {
    val tmpFile = File(videosDirectory, "$hash.mov.tmp")
    val encoder = AWTSequenceEncoder.createSequenceEncoder(tmpFile, fps)
    for (frameHash in frameHashes) {
      val frame = ImageIO.read(File(imagesDirectory, "$frameHash.png"))
      encoder.encodeImage(frame)
    }
    encoder.finish()
    tmpFile.renameTo(file)
  }
  return hash
}

/** Returns a SHA-1 hash of the pixels of [image]. */
private fun hash(image: BufferedImage): String {
  val hashingSink = HashingSink.sha1(blackholeSink())
  hashingSink.buffer().use { sink ->
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        sink.writeInt(image.getRGB(x, y))
      }
    }
  }
  return hashingSink.hash.hex()
}

/** Returns a SHA-1 hash of [lines]. */
private fun hash(lines: List<String>): String {
  val hashingSink = HashingSink.sha1(blackholeSink())
  hashingSink.buffer().use { sink ->
    for (hash in lines) {
      sink.writeUtf8(hash)
      sink.writeUtf8("\n")
    }
  }
  return hashingSink.hash.hex()
}

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
