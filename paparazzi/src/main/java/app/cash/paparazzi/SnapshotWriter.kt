package app.cash.paparazzi

import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import org.jcodec.api.awt.AWTSequenceEncoder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SnapshotWriter(
  private val rootDirectory: File = File("screenshots")
) : SnapshotHandler() {
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")
  private val shots = mutableListOf<Snapshot>()

  init {
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
  }

  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int
  ): FrameHandler {
    return object : FrameHandler {
      val hashes = mutableListOf<String>()

      override fun handle(image: BufferedImage) {
        hashes += writeImage(image)
      }

      override fun close() {
        if (hashes.size == 1) {
          shots += snapshot.copy(file = "images/${hashes[0]}.png")
        } else {
          val hash = writeVideo(hashes, fps)
          shots += snapshot.copy(file = "videos/$hash.mov")
        }
      }
    }
  }

  override fun close() = Unit

  /** Returns the hash of the image. */
  private fun writeImage(image: BufferedImage): String {
    val hash = hash(image)
    val file = File(rootDirectory, "images/${testName.toFilename()}.png")
    if (!file.exists()) {
      file.writeAtomically(image)
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

  private fun writeVideo(frameHashes: List<String>, fps: Int): String {
    val hash = hash(frameHashes)
    val file = File(rootDirectory, "videos/$hash.mov")
    if (!file.exists()) {
      val tmpFile = File(rootDirectory, "videos/$hash.mov.tmp")
      val encoder = AWTSequenceEncoder.createSequenceEncoder(tmpFile, fps)
      for (frameHash in frameHashes) {
        val frame = ImageIO.read(File(rootDirectory, "images/$frameHash.png"))
        encoder.encodeImage(frame)
      }
      encoder.finish()
      tmpFile.renameTo(file)
    }
    return hash
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

  private fun File.writeAtomically(bufferedImage: BufferedImage) {
    val tmpFile = File(parentFile, "$name.tmp")
    ImageIO.write(bufferedImage, "PNG", tmpFile)
    tmpFile.renameTo(this)
  }

  fun TestName.toFilename(): String {
    return "${packageName}_${className}_${methodName}"
  }
}