package app.cash.paparazzi.internal.apng

import app.cash.paparazzi.internal.ImageUtils.compareImages
import app.cash.paparazzi.internal.apng.TestPngUtils.createImage
import com.google.common.truth.Truth.assertThat
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Point
import java.io.File
import java.lang.AssertionError

class ApngVerifierTest {

  @get:Rule
  val tempFolderRule = TemporaryFolder()

  private val firstFrame = createImage(squareOffset = Point(5, 5))
  private val secondFrame = createImage(squareOffset = Point(25, 25))
  private val thirdFrame = createImage(squareOffset = Point(45, 45))

  @Test
  fun validatesSuccessfully() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 1,
      frameCount = 3,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(firstFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(thirdFrame)

      it.assertFinished()
    }
    assertThat(deltaFile.exists()).isFalse()
  }

  @Test
  fun failsWhenFpsDoesNotMatch() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 3,
      frameCount = 3,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(firstFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(thirdFrame)

      try {
        it.assertFinished()
        fail("Should have already failed")
      } catch (e: AssertionError) {
        assertThat(e.message).isEqualTo(
          "4 frames differed by more than 0.0%\n" +
            "Mismatched video fps expected: 1 actual: 3\n" +
            " - see details in file://${deltaFile.path}\n\n"
        )
      }
    }

    val expectedFile = javaClass.classLoader.getResource("delta-fps-mismatch.png")
    assertDeltaFile(deltaFile.toOkioPath(), expectedFile.path.toPath())
  }

  @Test
  fun writesRemainingExpectedFramesDuringAssertion() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 3,
      frameCount = 3,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(firstFrame)
      it.verifyFrame(secondFrame)

      try {
        it.assertFinished()
        fail("Should have already failed")
      } catch (e: AssertionError) {
        assertThat(e.message).isEqualTo(
          "4 frames differed by more than 0.0%\n" +
            "Mismatched video fps expected: 1 actual: 3\n" +
            " - see details in file://${deltaFile.path}\n\n"
        )
      }
    }

    val expectedFile = javaClass.classLoader.getResource("delta-mismatch-fps-missing-partial-frame.png")
    assertDeltaFile(deltaFile.toOkioPath(), expectedFile.path.toPath())
  }

  @Test
  fun failsWhenFpsDoesNotWithMoreFrames() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 3,
      frameCount = 3,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(firstFrame)
      it.verifyFrame(firstFrame)
      it.verifyFrame(firstFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(secondFrame)

      try {
        it.assertFinished()
        fail("Should have already failed")
      } catch (e: AssertionError) {
        assertThat(e.message).isEqualTo(
          "1 frames differed by more than 0.0%\n" +
            "Mismatched video fps expected: 1 actual: 3\n" +
            " - see details in file://${deltaFile.path}\n\n"
        )
      }
    }

    val expectedFile = javaClass.classLoader.getResource("delta-mismatch-fps-missing-whole-frame.png")
    assertDeltaFile(deltaFile.toOkioPath(), expectedFile.path.toPath())
  }

  @Test
  fun failsWhenFramesMismatch() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 1,
      frameCount = 3,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(thirdFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(firstFrame)

      try {
        it.assertFinished()
        fail("Should have already failed")
      } catch (e: AssertionError) {
        assertThat(e.message).isEqualTo(
          "2 frames differed by more than 0.0%\n" +
            " - see details in file://${deltaFile.path}\n\n"
        )
      }
    }

    val expectedFile = javaClass.classLoader.getResource("delta-frames-mismatch.png")
    assertDeltaFile(deltaFile.toOkioPath(), expectedFile.path.toPath())
  }

  @Test
  fun failsWhenGoldenHasLessFrames() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 1,
      frameCount = 5,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(firstFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(thirdFrame)
      it.verifyFrame(secondFrame)
      it.verifyFrame(firstFrame)

      try {
        it.assertFinished()
        fail("Should have already failed")
      } catch (e: AssertionError) {
        assertThat(e.message).isEqualTo(
          "2 frames differed by more than 0.0%\n" +
            "Mismatched frame count expected: 3 actual: 5\n" +
            " - see details in file://${deltaFile.path}\n\n"
        )
      }
    }

    val expectedFile = javaClass.classLoader.getResource("delta-less-frames.png")
    assertDeltaFile(deltaFile.toOkioPath(), expectedFile.path.toPath())
  }

  @Test
  fun failsWhenGoldenHasMoreFrames() {
    val file = javaClass.classLoader.getResource("simple_animation.png")
    val deltaFolder = tempFolderRule.newFolder()

    val goldenFilePath = file.path.toPath()
    val deltaFile = File(deltaFolder, "delta.png")
    ApngVerifier(
      goldenFilePath = goldenFilePath,
      deltaFilePath = deltaFile.toOkioPath(),
      fps = 1,
      frameCount = 2,
      maxPercentDifference = 0.0,
      withErrorText = false
    ).use {
      it.verifyFrame(firstFrame)
      it.verifyFrame(secondFrame)

      try {
        it.assertFinished()
        fail("Should have already failed")
      } catch (e: AssertionError) {
        assertThat(e.message).isEqualTo(
          "1 frames differed by more than 0.0%\n" +
            "Mismatched frame count expected: 3 actual: 2\n" +
            " - see details in file://${deltaFile.path}\n\n"
        )
      }
    }

    val expectedFile = javaClass.classLoader.getResource("delta-more-frames.png")
    assertDeltaFile(deltaFile.toOkioPath(), expectedFile.path.toPath())
  }

  private fun assertDeltaFile(actualPath: Path, expectedPath: Path) {
    ApngReader(FileSystem.SYSTEM.openReadOnly(actualPath)).use { actual ->
      ApngReader(FileSystem.SYSTEM.openReadOnly(expectedPath)).use { expected ->
        while (!expected.isFinished()) {
          val (_, percentDiff) = compareImages(expected.readNextFrame()!!, actual.readNextFrame()!!)
          assertThat(percentDiff).isEqualTo(0F)
        }
        assertThat(actual.isFinished()).isTrue()
        assertThat(actual.getFps()).isEqualTo(expected.getFps())
      }
    }
  }
}
