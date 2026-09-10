package app.cash.paparazzi.internal

import com.android.ide.common.rendering.api.RecyclableImage
import com.android.ide.common.rendering.api.RenderSession
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class RenderResultTest {
  @Test
  fun takeImageCopiesLogicalBoundsAcrossFramesWhenBufferIsReused() {
    val pooledImage = BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB)
    pooledImage.setRGB(49, 24, Color.RED.rgb)
    val firstRecyclableImage = FakeRecyclableImage(pooledImage, 200, 100)
    val secondRecyclableImage = FakeRecyclableImage(pooledImage, 50, 25)
    val session = FakeRenderSession(
      recyclableImages =
      ArrayDeque(
        listOf(
          firstRecyclableImage,
          secondRecyclableImage
        )
      )
    )

    val firstFrame = session.copyImage()
    val secondFrame = session.copyImage()

    assertThat(firstFrame.width).isEqualTo(200)
    assertThat(firstFrame.height).isEqualTo(100)
    assertThat(secondFrame.width).isEqualTo(50)
    assertThat(secondFrame.height).isEqualTo(25)
    assertThat(secondFrame.getRGB(49, 24)).isEqualTo(Color.RED.rgb)
    assertThat(firstRecyclableImage.isClosed).isTrue()
    assertThat(secondRecyclableImage.isClosed).isTrue()

    pooledImage.setRGB(49, 24, Color.BLUE.rgb)
    assertThat(secondFrame.getRGB(49, 24)).isEqualTo(Color.RED.rgb)
  }

  private class FakeRenderSession(
    private val recyclableImages: ArrayDeque<RecyclableImage>
  ) : RenderSession() {
    override fun getRecyclableImage(): RecyclableImage = recyclableImages.removeFirst()
  }

  private class FakeRecyclableImage(
    private val pooledImage: BufferedImage,
    private val logicalWidth: Int,
    private val logicalHeight: Int
  ) : RecyclableImage {
    var isClosed = false
      private set

    override fun getWidth(): Int = logicalWidth

    override fun getHeight(): Int = logicalHeight

    override fun getImage(): BufferedImage = pooledImage.getSubimage(0, 0, logicalWidth, logicalHeight)

    override fun close() {
      isClosed = true
    }
  }
}
