package app.cash.paparazzi

import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.SnapshotHandler.FrameHandler
import app.cash.paparazzi.SnapshotHandler.LazyFrameHandler
import com.android.resources.ScreenRound
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.awt.image.BufferedImage

class LazyFrameHandlerTest {
  @Test
  fun unusedProviderDoesNotPrepareImage() {
    withLazyFrame({ error("Image should not be prepared") }) { }
  }

  @Test
  fun providerMemoizesAndExpires() {
    var calls = 0
    lateinit var retained: () -> BufferedImage
    withLazyFrame({
      calls++
      BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB)
    }) { image ->
      retained = image
      assertThat(image()).isSameInstanceAs(image())
      assertThat(calls).isEqualTo(1)
    }
    assertThrows(IllegalStateException::class.java) { retained() }
  }

  @Test
  fun unusedProviderExpiresAndCannotBeUsedFromAnotherThread() {
    lateinit var retained: () -> BufferedImage
    withLazyFrame({ error("Image should not be prepared") }) { image ->
      retained = image
      var failure: Throwable? = null
      Thread {
        failure = runCatching { image() }.exceptionOrNull()
      }.apply {
        start()
        join()
      }
      assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }
    assertThrows(IllegalStateException::class.java) { retained() }
  }

  @Test
  fun providerExpiresAfterHandlerAndPreparationFailures() {
    for (failDuringPreparation in listOf(false, true)) {
      lateinit var retained: () -> BufferedImage
      val failure = IllegalArgumentException("expected")
      val thrown = assertThrows(IllegalArgumentException::class.java) {
        withLazyFrame({ throw failure }) { image ->
          retained = image
          if (failDuringPreparation) image() else throw failure
        }
      }
      assertThat(thrown).isSameInstanceAs(failure)
      assertThrows(IllegalStateException::class.java) { retained() }
    }
  }

  @Test
  fun alreadyPreparedImageUsesTheSameProviderContract() {
    lateinit var retained: () -> BufferedImage
    val expected = BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB)
    val handler = object : LazyFrameHandler {
      override fun handle(image: () -> BufferedImage) {
        retained = image
        assertThat(image()).isSameInstanceAs(expected)
        assertThat(image()).isSameInstanceAs(expected)
      }
      override fun close() = Unit
    }
    handler.handle(expected)
    assertThrows(IllegalStateException::class.java) { retained() }
  }

  @Test
  fun discardedFramesStillRenderAdvanceTimeAndCloseHandlers() {
    val eager = renderAnimation(lazy = false)
    val discarded = renderAnimation(lazy = true)
    assertThat(discarded).isEqualTo(eager)
    assertThat(discarded.drawTimes).containsAtLeast(100L, 350L, 600L)
    assertThat(discarded.callbacks).isEqualTo(3)
    assertThat(discarded.closes).isEqualTo(1)
  }

  @Test
  fun lazyAndEagerImagesMatchAcrossHandlerChanges() {
    for (round in listOf(false, true)) {
      for (useDeviceResolution in listOf(false, true)) {
        val images = mutableListOf<BufferedImage>()
        var index = 0
        val snapshotHandler = object : SnapshotHandler {
          override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler {
            return if (index++ == 1) {
              object : LazyFrameHandler {
                override fun handle(image: () -> BufferedImage) {
                  assertThat(image()).isSameInstanceAs(image())
                  images += image().copy()
                }
                override fun close() = Unit
              }
            } else {
              object : FrameHandler {
                override fun handle(image: BufferedImage) {
                  images += image.copy()
                }
                override fun close() = Unit
              }
            }
          }
          override fun close() = Unit
        }
        val config = DeviceConfig.PIXEL_5.copy(screenRound = if (round) ScreenRound.ROUND else ScreenRound.NOTROUND)
        val paparazzi = Paparazzi(
          deviceConfig = config,
          snapshotHandler = snapshotHandler,
          useDeviceResolution = useDeviceResolution
        )
        paparazzi.setup(TestName("app.cash.paparazzi", "LazyFrameHandlerTest", "switchHandlers"))
        try {
          val view = TextView(paparazzi.context).apply {
            text = "Unchanged pixels"
            setBackgroundColor(Color.BLUE)
          }
          repeat(3) { paparazzi.snapshot(view) }
          assertThat(images).hasSize(3)
          val expected = images.first()
          assertThat(expected.height).isEqualTo(if (useDeviceResolution) 2340 else 1000)
          for (image in images.drop(1)) {
            assertThat(image.width).isEqualTo(expected.width)
            assertThat(image.height).isEqualTo(expected.height)
            assertThat(image.pixels()).isEqualTo(expected.pixels())
          }
          if (round) assertThat(expected.getRGB(0, 0)).isEqualTo(0)
        } finally {
          paparazzi.teardown()
        }
      }
    }
  }

  @Test
  fun discardedFramesStillApplyAndRemoveRenderExtensions() {
    var extensions = 0
    val extension = object : RenderExtension {
      override fun renderView(contentView: View): View =
        FrameLayout(contentView.context).apply {
          extensions++
          addView(contentView)
        }
    }
    val handler = DiscardingSnapshotHandler()
    val paparazzi = Paparazzi(snapshotHandler = handler, renderExtensions = setOf(extension))
    paparazzi.setup(TestName("app.cash.paparazzi", "LazyFrameHandlerTest", "extensions"))
    try {
      val view = View(paparazzi.context)
      repeat(2) {
        paparazzi.snapshot(view)
        assertThat(view.parent).isNull()
      }
      assertThat(extensions).isEqualTo(2)
      assertThat(handler.frames).isEqualTo(2)
      assertThat(handler.closes).isEqualTo(2)
    } finally {
      paparazzi.teardown()
    }
  }

  @Suppress("DEPRECATION")
  @Test
  fun discardedFramesDoNotBypassAccessibilityValidation() {
    val handler = DiscardingSnapshotHandler()
    val paparazzi = Paparazzi(
      snapshotHandler = handler,
      validateAccessibility = true,
      renderExtensions = setOf(object : RenderExtension {
        override fun renderView(contentView: View): View = contentView
      })
    )
    paparazzi.setup(TestName("app.cash.paparazzi", "LazyFrameHandlerTest", "accessibility"))
    try {
      val view = View(paparazzi.context)
      val thrown = assertThrows(IllegalArgumentException::class.java) { paparazzi.snapshot(view) }
      assertThat(thrown).hasMessageThat().contains("accessibility validation and render extensions")
      assertThat(handler.frames).isEqualTo(0)
      assertThat(handler.closes).isEqualTo(1)
      assertThat(view.parent).isNull()
    } finally {
      paparazzi.teardown()
    }
  }

  @Test
  fun handlerFailureStillClosesAndCleansUpRendering() {
    val failure = IllegalArgumentException("handler failed")
    var closes = 0
    val handler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler =
        object : LazyFrameHandler {
          override fun handle(image: () -> BufferedImage) {
            throw failure
          }
          override fun close() {
            closes++
          }
        }
      override fun close() = Unit
    }
    val paparazzi = Paparazzi(snapshotHandler = handler)
    paparazzi.setup(TestName("app.cash.paparazzi", "LazyFrameHandlerTest", "failure"))
    try {
      val view = View(paparazzi.context)
      assertThat(
        assertThrows(IllegalArgumentException::class.java) {
          paparazzi.snapshot(view)
        }
      ).isSameInstanceAs(failure)
      assertThat(closes).isEqualTo(1)
      assertThat(view.parent).isNull()
    } finally {
      paparazzi.teardown()
    }
  }

  @Test
  fun publicSdkCallbacksStillReceivePreparedImages() {
    val images = mutableListOf<BufferedImage>()
    val sdk = PaparazziSdk(deviceConfig = DeviceConfig.PIXEL_5, onNewFrame = { images += it.copy() })
    sdk.setup()
    sdk.prepare()
    try {
      sdk.snapshot(View(sdk.context))
      assertThat(images).hasSize(1)
      assertThat(images.single().height).isEqualTo(1000)
      assertThat(images.single().width).isEqualTo(461)
    } finally {
      sdk.teardown()
    }
  }

  private fun renderAnimation(lazy: Boolean): RenderResult {
    val drawTimes = mutableListOf<Long>()
    var callbacks = 0
    var closes = 0
    val handler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler =
        if (lazy) {
          object : LazyFrameHandler {
            override fun handle(image: () -> BufferedImage) {
              callbacks++
            }
            override fun close() {
              closes++
            }
          }
        } else {
          object : FrameHandler {
            override fun handle(image: BufferedImage) {
              callbacks++
            }
            override fun close() {
              closes++
            }
          }
        }
      override fun close() = Unit
    }
    val paparazzi = Paparazzi(snapshotHandler = handler, deviceConfig = DeviceConfig.PIXEL_5)
    paparazzi.setup(TestName("app.cash.paparazzi", "LazyFrameHandlerTest", "animation"))
    try {
      val view = object : View(paparazzi.context) {
        override fun onDraw(canvas: Canvas) {
          drawTimes += SystemClock.uptimeMillis()
          invalidate()
        }
      }
      paparazzi.gif(view, start = 100, end = 600, fps = 4)
    } finally {
      paparazzi.teardown()
    }
    return RenderResult(drawTimes, callbacks, closes)
  }

  private data class RenderResult(val drawTimes: List<Long>, val callbacks: Int, val closes: Int)

  private class DiscardingSnapshotHandler : SnapshotHandler {
    var frames = 0
    var closes = 0
    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler =
      object : LazyFrameHandler {
        override fun handle(image: () -> BufferedImage) {
          frames++
        }
        override fun close() {
          closes++
        }
      }
    override fun close() = Unit
  }

  private fun BufferedImage.copy(): BufferedImage =
    BufferedImage(colorModel, copyData(null), isAlphaPremultiplied, null)

  private fun BufferedImage.pixels(): IntArray = getRGB(0, 0, width, height, null, 0, width)
}
