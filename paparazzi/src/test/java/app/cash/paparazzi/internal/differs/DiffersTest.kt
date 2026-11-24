package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ.DiffResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.imageio.ImageIO

class DiffersTest {
  private val classLoader = DiffersTest::class.java.classLoader

  @Test
  fun `test all differs with widgets`() {
    val linuxWidget = ImageIO.read(classLoader.getResourceAsStream("differs/linux_widget.png"))
    val macosxWidget = ImageIO.read(classLoader.getResourceAsStream("differs/macosx_widget.png"))

    val pixelPerfectResult1 = PixelPerfect.compare(linuxWidget, macosxWidget)
    assertThat(pixelPerfectResult1).isInstanceOf(DiffResult.Different::class.java)
    with(pixelPerfectResult1 as DiffResult.Different) {
      assertThat(percentDifference).isEqualTo(3.5594177E-5f)
      assertThat(numDifferentPixels).isEqualTo(40)
    }

    val offByTwoResult1 = OffByTwo.compare(linuxWidget, macosxWidget)
    assertThat(offByTwoResult1).isInstanceOf(DiffResult.Similar::class.java)
    with(offByTwoResult1 as DiffResult.Similar) {
      assertThat(numSimilarPixels).isEqualTo(40)
    }

    val ssimResult1 = Mssim.compare(linuxWidget, macosxWidget)
    with(ssimResult1) {
      assertThat(this).isInstanceOf(DiffResult.Similar::class.java)
      this as DiffResult.Similar
      assertThat(numSimilarPixels).isEqualTo(215829)
    }

    val flipResult1 = Flip.compare(linuxWidget, macosxWidget)
    assertThat(flipResult1).isInstanceOf(DiffResult.Identical::class.java)

    val siftResult1 = Sift.compare(linuxWidget, macosxWidget)
    with(siftResult1) {
      assertThat(this).isInstanceOf(DiffResult.Similar::class.java)
      this as DiffResult.Similar
      assertThat(numSimilarPixels).isEqualTo(293)
    }

    val de2000Result1 = DeltaE2000.compare(linuxWidget, macosxWidget)
    assertThat(de2000Result1).isInstanceOf(DiffResult.Identical::class.java)
  }

  @Test
  fun `test all differs with full screens`() {
    val linuxFullScreen = ImageIO.read(classLoader.getResourceAsStream("differs/linux_full_screen.png"))
    val macosxFullScreen = ImageIO.read(classLoader.getResourceAsStream("differs/macosx_full_screen.png"))

    val pixelPerfectResult2 = PixelPerfect.compare(linuxFullScreen, macosxFullScreen)
    assertThat(pixelPerfectResult2).isInstanceOf(DiffResult.Different::class.java)
    with(pixelPerfectResult2 as DiffResult.Different) {
      assertThat(percentDifference).isEqualTo(3.9629595E-6f)
      assertThat(numDifferentPixels).isEqualTo(44)
    }

    val offByTwoResult2 = OffByTwo.compare(linuxFullScreen, macosxFullScreen)
    assertThat(offByTwoResult2).isInstanceOf(DiffResult.Similar::class.java)
    with(offByTwoResult2 as DiffResult.Similar) {
      assertThat(numSimilarPixels).isEqualTo(44)
    }

    val ssimResult2 = Mssim.compare(linuxFullScreen, macosxFullScreen)
    with(ssimResult2) {
      assertThat(this).isInstanceOf(DiffResult.Similar::class.java)
      this as DiffResult.Similar
      assertThat(numSimilarPixels).isEqualTo(2332799)
    }

    val flipResult2 = Flip.compare(linuxFullScreen, macosxFullScreen)
    assertThat(flipResult2).isInstanceOf(DiffResult.Identical::class.java)

    //  Java heap space error (OOM)

//    val siftResult2 = Sift.compare(linuxFullScreen, macosxFullScreen)
//    with(siftResult2) {
//      assertThat(this).isInstanceOf(DiffResult.Similar::class.java)
//      this as DiffResult.Similar
//      assertThat(numSimilarPixels).isEqualTo(293)
//    }

    val de2000Result2 = DeltaE2000.compare(linuxFullScreen, macosxFullScreen)
    assertThat(de2000Result2).isInstanceOf(DiffResult.Identical::class.java)
  }
}
