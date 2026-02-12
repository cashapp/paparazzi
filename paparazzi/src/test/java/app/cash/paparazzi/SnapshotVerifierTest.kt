package app.cash.paparazzi

import app.cash.paparazzi.FileSubject.Companion.assertThat
import app.cash.paparazzi.accessibility.ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME
import app.cash.paparazzi.internal.differs.PixelPerfect
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.Date

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
