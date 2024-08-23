package app.cash.paparazzi

import app.cash.paparazzi.FileSubject.Companion.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant

class SnapshotVerifierTest {
  // Verify that the snapshot verifier reads the correct file name output from HtmlReportWriter
  @get:Rule
  val reportRoot: TemporaryFolder = TemporaryFolder()

  @get:Rule
  val snapshotRoot: TemporaryFolder = TemporaryFolder()

  private val anyImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

  @Test
  fun verifyImageFilename() {
    try {
      // set record mode
      System.setProperty("paparazzi.test.record", "true")
      val imageSnapshot = Snapshot(
        name = "image name with spaces",
        testName = TestName("app.cash.paparazzi", "CelebrityTest", "test name with spaces"),
        timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate(),
        tags = listOf("redesign")
      )
      val htmlReportWriter = HtmlReportWriter("run_one", reportRoot.root, snapshotRoot.root)
      htmlReportWriter.use {
        val golden =
          File("${snapshotRoot.root}/images/app.cash.paparazzi_CelebrityTest_test_name_with_spaces_image_name_with_spaces.png")

        // precondition
        assertThat(golden).doesNotExist()

        val recordFrameHandler = htmlReportWriter.newFrameHandler(
          snapshot = imageSnapshot,
          frameCount = 1,
          fps = -1
        )
        recordFrameHandler.use { recordFrameHandler.handle(anyImage) }
        assertThat(golden).exists()
      }

      val snapshotVerifier = SnapshotVerifier(0.0, snapshotRoot.root)
      snapshotVerifier.use {
        val verifyFrameHandler = snapshotVerifier.newFrameHandler(
          snapshot = imageSnapshot,
          frameCount = 1,
          fps = -1,
        )
        verifyFrameHandler.use { verifyFrameHandler.handle(anyImage) }
      }
    } finally {
      System.clearProperty("paparazzi.test.record")
    }
  }

  @Test
  fun verifyVideoFilename() {

  }
}
