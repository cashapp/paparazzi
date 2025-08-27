package app.cash.paparazzi.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.graphics.ImageFormat.YUV_420_888
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
import android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
import android.media.Image
import android.media.ImageReader
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.ScrollCaptureSession
import android.view.ScrollCaptureTarget
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.IconToggleButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnPreDraw
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select


class ScrollingSnapshotTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun test() {
    val view = paparazzi.inflate<ScrollView>(R.layout.scroll)
    val targets = mutableListOf<ScrollCaptureTarget>()

    view.doOnPreDraw {
      val localVisibleRect = Rect().also(view::getLocalVisibleRect)
      val locationInWindow = IntArray(2)
      view.getLocationInWindow(locationInWindow)

      println("locationInWindow: ${locationInWindow[0]},${locationInWindow[1]} localVisibleRect: $localVisibleRect")

      view.onScrollCaptureSearch(
        localVisibleRect,
        Point(locationInWindow[0], locationInWindow[1]),
        targets::add
      )

      println("Targets: ${targets.size}")
      if (targets.isEmpty()) {
        return@doOnPreDraw
      }

      val target = targets.single()
      val callback = target.callback
      val readRects = mutableListOf<Rect>()
      callback.onScrollCaptureSearch(CancellationSignal()) {
        readRects += it
      }
      val scrollBounds = target.scrollBounds ?: readRects.first()
      val captureWidth = scrollBounds.width()
      val captureHeight = DeviceConfig.PIXEL.screenHeight

      ImageReader.newInstance(
        captureWidth,
        captureHeight,
        PixelFormat.RGBA_8888,
        // Each image is read, processed, and closed before the next request to draw is
        // made,
        // so we don't need multiple images.
        /* maxImages= */ 1,
        USAGE_GPU_SAMPLED_IMAGE or USAGE_GPU_COLOR_OUTPUT,
      ).use {
        runBlocking {
          val bitmapsChannel = Channel<Bitmap>(capacity = Channel.RENDEZVOUS)

          val imageCollectorJob =
            launch(start = CoroutineStart.UNDISPATCHED) {
              it.collectImages { b ->
                println("Image received ${b.width}x${b.height}")
                val bitmap = b.toSoftwareBitmap()
                bitmapsChannel.send(bitmap)
              }
            }

          try {
            val session = ScrollCaptureSession(it.surface, scrollBounds, target.positionInWindow)
            callback.onScrollCaptureStart(session, CancellationSignal()) {
              println("onReady - onScrollCaptureStart")
            }
            val captureOffset = Point(0, 0)
            val captureWindowHeight: Int = captureHeight
            val requestedCaptureArea =
              Rect(
                captureOffset.x,
                captureOffset.y,
                captureOffset.x + captureWidth,
                captureOffset.y + captureWindowHeight,
              )
            var resultCaptureArea: Rect? = null
            callback.onScrollCaptureImageRequest(
              session,
              CancellationSignal(),
              requestedCaptureArea,
            ) {
              resultCaptureArea = it
              println("onComplete - onScrollCaptureImageRequest")
            }
            bitmapsChannel.receive()

            if (resultCaptureArea != null) {
              val bitmap =
                if (!resultCaptureArea.isEmpty) {
                  bitmapsChannel.receiveWithTimeout(1_000) {
                    "No bitmap received after 1 second for capture area " +
                      resultCaptureArea
                  }
                } else {
                  null
                }

              if (bitmap != null) {
                try {
                  FileOutputStream("scroll-output.png").use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // bmp is your Bitmap instance
                  }
                } catch (e: IOException) {
                  e.printStackTrace()
                }
              }
            }

            callback.onScrollCaptureEnd {
              println("onReady - onScrollCaptureEnd")
            }
          } finally {
            // ImageReader has no signal that it's finished, so in the happy path we have to
            // stop the collector job explicitly.
            imageCollectorJob.cancel()
            bitmapsChannel.close()
          }
        }
      }
    }

    paparazzi.snapshot(view)
  }

/**
 * Reads all images from this [ImageReader] and passes them to [onImage]. The [Image] will
 * automatically be closed when [onImage] returns.
 *
 * Propagates backpressure to the [ImageReader] – only one image will be acquired from the
 * [ImageReader] at a time, and the next image won't be acquired until [onImage] returns.
 */
private suspend inline fun ImageReader.collectImages(onImage: (Image) -> Unit): Nothing {
  val imageAvailableChannel = Channel<Unit>(capacity = Channel.CONFLATED)
  setOnImageAvailableListener(
    { imageAvailableChannel.trySend(Unit) },
    Handler(Looper.getMainLooper()),
  )
  val context = currentCoroutineContext()

  try {
    // Read all images until cancelled.
    while (true) {
      context.ensureActive()
      // Fast path – if an image is immediately available, don't suspend.
      var image: Image? = acquireNextImage()
      // If no image was available, suspend until the callback fires.
      while (image == null) {
        imageAvailableChannel.receive()
        image = acquireNextImage()
      }
      image.use { onImage(image) }
    }
  } finally {
    setOnImageAvailableListener(null, null)
  }
}

/**
 * Helper function for converting an [Image] to a [Bitmap] by copying the hardware buffer into a
 * software bitmap.
 */
private fun Image.toSoftwareBitmap(): Bitmap {
  val hardwareBuffer = requireNotNull(hardwareBuffer) { "No hardware buffer" }
  hardwareBuffer.use {
    val hardwareBitmap =
      Bitmap.wrapHardwareBuffer(hardwareBuffer, ColorSpace.get(ColorSpace.Named.SRGB))
        ?: error("wrapHardwareBuffer returned null")
    try {
      return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
    } finally {
      hardwareBitmap.recycle()
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend inline fun <E> ReceiveChannel<E>.receiveWithTimeout(
  timeoutMillis: Long,
  crossinline timeoutMessage: () -> String,
): E = select {
  onReceive { it }
  onTimeout(timeoutMillis) { error(timeoutMessage()) }
}
  }
