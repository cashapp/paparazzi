package app.cash.paparazzi.internal

import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import java.awt.image.BufferedImage
import java.io.File

/** Returns the hash of the image. */
fun BufferedImage.writeImage(directory: File): String {
  val hash = this.hash()
  val file = File(directory, "$hash.png")
  if (!file.exists()) {
    file.writeAtomically(this)
  }
  return hash
}

/** Returns a SHA-1 hash of the pixels of [image]. */
fun BufferedImage.hash(): String {
  val hashingSink = HashingSink.sha1(blackholeSink())
  hashingSink.buffer().use { sink ->
    for (y in 0 until this.height) {
      for (x in 0 until this.width) {
        sink.writeInt(this.getRGB(x, y))
      }
    }
  }
  return hashingSink.hash.hex()
}
