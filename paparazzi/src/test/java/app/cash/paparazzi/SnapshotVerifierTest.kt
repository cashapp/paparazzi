package app.cash.paparazzi

import app.cash.paparazzi.FileSubject.Companion.assertThat
import app.cash.paparazzi.accessibility.ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME
import app.cash.paparazzi.internal.differs.PixelPerfect
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.util.Date
import javax.imageio.ImageIO

class SnapshotVerifierTest {
  @get:Rule
  val snapshotRoot: TemporaryFolder = TemporaryFolder()

  @get:Rule
  val buildRoot: TemporaryFolder = TemporaryFolder()

  @Test
  fun `accessibility artifact compare ignores JSON formatting differences`() {
    withBuildDir {
      val snapshot = testSnapshot()
      val expectedFile = snapshot.artifactFile(
        ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
        File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
      )
      expectedFile.parentFile.mkdirs()
      expectedFile.writeText(
        """
          |{
          |  "legendText": "First",
          |  "beforeElementId": "First",
          |  "afterElementId": "Second"
          |}
        """.trimMargin()
      )

      SnapshotVerifier(
        maxPercentDifference = 0.0,
        rootDirectory = snapshotRoot.root,
        differ = PixelPerfect
      ).use { snapshotVerifier ->
        val frameHandler = snapshotVerifier.newFrameHandler(
          snapshot = snapshot,
          frameCount = 1,
          fps = -1
        )
        frameHandler.use {
          frameHandler.handleArtifact(
            ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
            """
              |{
              |  "afterElementId": "Second",
              |  "beforeElementId": "First",
              |  "legendText": "First"
              |}
            """.trimMargin()
          )
        }
      }
    }
  }

  @Test
  fun `accessibility artifact compare fails when golden is missing`() {
    withBuildDir {
      val snapshot = testSnapshot()
      val expectedFile = snapshot.artifactFile(
        ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
        File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
      )
      val actualContent = """{"legendText":"First"}"""

      val error = assertThrows(AssertionError::class.java) {
        SnapshotVerifier(
          maxPercentDifference = 0.0,
          rootDirectory = snapshotRoot.root,
          differ = PixelPerfect
        ).use { snapshotVerifier ->
          val frameHandler = snapshotVerifier.newFrameHandler(
            snapshot = snapshot,
            frameCount = 1,
            fps = -1
          )
          frameHandler.use {
            frameHandler.handleArtifact(
              ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
              actualContent
            )
          }
        }
      }

      assertThat(error).hasMessageThat().contains(expectedFile.path)
      val failureDir = File(buildRoot.root, "paparazzi/failures")
      val actualFiles = failureDir.listFiles { _, name -> name.startsWith("actual-") } ?: emptyArray()
      val diffFiles = failureDir.listFiles { _, name -> name.startsWith("diff-") } ?: emptyArray()
      assertThat(actualFiles).hasLength(1)
      assertThat(diffFiles).hasLength(1)
      assertThat(actualFiles.single()).hasContent(actualContent)
      assertThat(diffFiles.single().readText()).contains("Golden artifact not found")
    }
  }

  @Test
  fun `accessibility artifact compare writes actual and diff files when different`() {
    withBuildDir {
      val snapshot = testSnapshot()
      val expectedFile = snapshot.artifactFile(
        ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
        File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
      )
      expectedFile.parentFile.mkdirs()
      expectedFile.writeText("""{"legendText":"Expected"}""")

      val error = assertThrows(AssertionError::class.java) {
        SnapshotVerifier(
          maxPercentDifference = 0.0,
          rootDirectory = snapshotRoot.root,
          differ = PixelPerfect
        ).use { snapshotVerifier ->
          val frameHandler = snapshotVerifier.newFrameHandler(
            snapshot = snapshot,
            frameCount = 1,
            fps = -1
          )
          frameHandler.use {
            frameHandler.handleArtifact(
              ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
              """{"legendText":"Actual"}"""
            )
          }
        }
      }

      val failureDir = File(buildRoot.root, "paparazzi/failures")
      val actualFiles = failureDir.listFiles { _, name -> name.startsWith("actual-") } ?: emptyArray()
      val diffFiles = failureDir.listFiles { _, name -> name.startsWith("diff-") } ?: emptyArray()

      assertThat(error).hasMessageThat().contains(expectedFile.path)
      assertThat(actualFiles).hasLength(1)
      assertThat(diffFiles).hasLength(1)
      assertThat(actualFiles.single()).hasContent("""{"legendText":"Actual"}""")
      assertThat(diffFiles.single().readText()).contains("Artifact mismatch")
      assertThat(diffFiles.single().readText()).contains("Expected")
      assertThat(diffFiles.single().readText()).contains("Actual")
    }
  }

  @Test
  fun `successful artifact retry removes stale actual and diff files`() {
    withBuildDir {
      val snapshot = testSnapshot()
      val expectedFile = snapshot.artifactFile(
        ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
        File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
      )
      expectedFile.parentFile.mkdirs()
      expectedFile.writeText("""{"legendText":"Expected"}""")
      val snapshotVerifier = SnapshotVerifier(
        maxPercentDifference = 0.0,
        rootDirectory = snapshotRoot.root,
        differ = PixelPerfect
      )

      assertThrows(AssertionError::class.java) {
        snapshotVerifier.newFrameHandler(snapshot, frameCount = 1, fps = -1).use { frameHandler ->
          frameHandler.handleArtifact(
            ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
            """{"legendText":"Actual"}"""
          )
        }
      }
      val failureDir = File(buildRoot.root, "paparazzi/failures")
      assertThat(failureDir.listFiles { _, name -> name.startsWith("actual-") }).hasLength(1)
      assertThat(failureDir.listFiles { _, name -> name.startsWith("diff-") }).hasLength(1)

      expectedFile.writeText("""{"legendText":"Actual"}""")
      snapshotVerifier.newFrameHandler(snapshot, frameCount = 1, fps = -1).use { frameHandler ->
        frameHandler.handleArtifact(
          ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
          """{"legendText":"Actual"}"""
        )
      }

      assertThat(failureDir.listFiles { _, name -> name.startsWith("actual-") }).isEmpty()
      assertThat(failureDir.listFiles { _, name -> name.startsWith("diff-") }).isEmpty()
    }
  }

  @Test
  fun `non accessibility artifacts are compared and can fail`() {
    withBuildDir {
      val snapshot = testSnapshot()
      val artifactName = "debug-info.txt"
      val expectedFile = snapshot.artifactFile(
        artifactName,
        File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
      )
      expectedFile.parentFile.mkdirs()
      expectedFile.writeText("Expected")

      val error = assertThrows(AssertionError::class.java) {
        SnapshotVerifier(
          maxPercentDifference = 0.0,
          rootDirectory = snapshotRoot.root,
          differ = PixelPerfect
        ).use { snapshotVerifier ->
          val frameHandler = snapshotVerifier.newFrameHandler(
            snapshot = snapshot,
            frameCount = 1,
            fps = -1
          )
          frameHandler.use {
            frameHandler.handleArtifact(artifactName, "Actual")
          }
        }
      }

      val failureDir = File(buildRoot.root, "paparazzi/failures")
      val actualFiles = failureDir.listFiles { _, name -> name.startsWith("actual-") } ?: emptyArray()
      val diffFiles = failureDir.listFiles { _, name -> name.startsWith("diff-") } ?: emptyArray()

      assertThat(error).hasMessageThat().contains(expectedFile.path)
      assertThat(actualFiles).hasLength(1)
      assertThat(diffFiles).hasLength(1)
      assertThat(actualFiles.single()).hasContent("Actual")
    }
  }

  @Test
  fun `non JSON artifacts use exact text comparison`() {
    withBuildDir {
      val snapshot = testSnapshot()
      val artifactName = "debug-info.txt"
      val expectedFile = snapshot.artifactFile(
        artifactName,
        File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
      )
      expectedFile.parentFile.mkdirs()
      expectedFile.writeText("""{"first":1,"second":2}""")

      assertThrows(AssertionError::class.java) {
        SnapshotVerifier(
          maxPercentDifference = 0.0,
          rootDirectory = snapshotRoot.root,
          differ = PixelPerfect
        ).use { snapshotVerifier ->
          snapshotVerifier.newFrameHandler(snapshot, frameCount = 1, fps = -1).use { frameHandler ->
            frameHandler.handleArtifact(artifactName, """{"second":2,"first":1}""")
          }
        }
      }
    }
  }

  @Test
  fun `native artifact mismatch writes diagnostic image and successful retry removes it`() {
    withBuildDir {
      withReportType("native") {
        val snapshot = testSnapshot()
        val golden = snapshot.artifactFile(
          ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
          File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
        )
        golden.parentFile.mkdirs()
        golden.writeText("expected")
        val verifier = SnapshotVerifier(0.0, snapshotRoot.root, PixelPerfect)
        val image = BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB).apply { setRGB(5, 5, -1) }
        var imageRequests = 0
        val failureImage = {
          imageRequests++
          image
        }

        assertThrows(AssertionError::class.java) {
          (verifier.newFrameHandler(snapshot, 1, -1) as ArtifactFrameHandler)
            .handleArtifact(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME, "actual", failureImage)
        }
        val imageFile =
          File(buildRoot.root, "paparazzi/failures/accessibility-${snapshot.toFileName(extension = "png")}")
        assertThat(imageRequests).isEqualTo(1)
        assertThat(ImageIO.read(imageFile).getRGB(5, 5)).isEqualTo(-1)
        assertThat(golden).hasContent("expected")

        golden.writeText("actual")
        (verifier.newFrameHandler(snapshot, 1, -1) as ArtifactFrameHandler)
          .handleArtifact(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME, "actual", failureImage)
        assertThat(imageRequests).isEqualTo(1)
        assertThat(imageFile.exists()).isFalse()
      }
    }
  }

  @Test
  fun `native missing artifact writes diagnostic image and preserves assertion if image fails`() {
    withBuildDir {
      withReportType("native") {
        val snapshot = testSnapshot()
        val handler = SnapshotVerifier(0.0, snapshotRoot.root, PixelPerfect)
          .newFrameHandler(snapshot, 1, -1) as ArtifactFrameHandler
        assertThrows(AssertionError::class.java) {
          handler.handleArtifact(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME, "actual") {
            BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB)
          }
        }
        val imageFile =
          File(buildRoot.root, "paparazzi/failures/accessibility-${snapshot.toFileName(extension = "png")}")
        assertThat(ImageIO.read(imageFile).width).isEqualTo(20)

        val imageFailure = IllegalStateException("diagnostic rendering failed")
        val failure = assertThrows(AssertionError::class.java) {
          handler.handleArtifact(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME, "actual") { throw imageFailure }
        }
        assertThat(failure).hasMessageThat().contains("not found")
        assertThat(failure.suppressed).asList().containsExactly(imageFailure)
      }
    }
  }

  @Test
  fun `legacy and default reports do not request artifact failure images`() {
    withBuildDir {
      for (reportType in listOf(null, "legacy")) {
        withReportType(reportType) {
          val handler = SnapshotVerifier(0.0, snapshotRoot.root, PixelPerfect)
            .newFrameHandler(testSnapshot(), 1, -1) as ArtifactFrameHandler
          val failure = assertThrows(AssertionError::class.java) {
            handler.handleArtifact(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME, "actual") {
              error("Image should not be requested")
            }
          }
          assertThat(failure.suppressed).isEmpty()
          assertThat(
            File(buildRoot.root, "paparazzi/failures").listFiles { _, name ->
              name.endsWith(".png")
            }
          ).isEmpty()
        }
      }
    }
  }

  @Test
  fun `native passing and non accessibility artifacts do not request failure images`() {
    withBuildDir {
      withReportType("native") {
        val snapshot = testSnapshot()
        val golden = snapshot.artifactFile(
          ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME,
          File(snapshotRoot.root, ARTIFACTS_DIRECTORY_NAME)
        )
        golden.parentFile.mkdirs()
        golden.writeText("actual")
        val handler = SnapshotVerifier(0.0, snapshotRoot.root, PixelPerfect)
          .newFrameHandler(snapshot, 1, -1) as ArtifactFrameHandler
        handler.handleArtifact(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME, "actual") {
          error("Image should not be requested")
        }
        val failure = assertThrows(AssertionError::class.java) {
          handler.handleArtifact("debug.txt", "actual") { error("Image should not be requested") }
        }
        assertThat(failure.suppressed).isEmpty()
        assertThat(File(buildRoot.root, "paparazzi/failures").listFiles { _, name -> name.endsWith(".png") }).isEmpty()
      }
    }
  }

  private inline fun withReportType(reportType: String?, block: () -> Unit) {
    val original = System.getProperty("paparazzi.reportType")
    try {
      if (reportType == null) {
        System.clearProperty("paparazzi.reportType")
      } else {
        System.setProperty("paparazzi.reportType", reportType)
      }
      block()
    } finally {
      if (original == null) {
        System.clearProperty("paparazzi.reportType")
      } else {
        System.setProperty("paparazzi.reportType", original)
      }
    }
  }

  private fun testSnapshot() =
    Snapshot(
      name = "loading",
      testName = TestName("app.cash.paparazzi", "VerifyTest", "verify"),
      timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate()
    )

  private inline fun withBuildDir(block: () -> Unit) {
    val propertyName = "paparazzi.failures.dir"
    val originalFailuresDir = System.getProperty(propertyName)
    try {
      System.setProperty(propertyName, File(buildRoot.root, "paparazzi/failures").absolutePath)
      block()
    } finally {
      if (originalFailuresDir == null) {
        System.clearProperty(propertyName)
      } else {
        System.setProperty(propertyName, originalFailuresDir)
      }
    }
  }

  private fun Instant.toDate() = Date(toEpochMilli())
}
