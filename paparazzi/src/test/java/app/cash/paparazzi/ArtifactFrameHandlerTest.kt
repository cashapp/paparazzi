package app.cash.paparazzi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtifactFrameHandlerTest {
  @Test
  fun `artifact handling does not extend the public frame handler contract`() {
    assertThat(SnapshotHandler.FrameHandler::class.java.declaredMethods.map { it.name })
      .doesNotContain("handleArtifact")

    val artifacts = mutableListOf<String>()
    val handler = ArtifactFrameHandler { _, content -> artifacts += content }

    handler.handleArtifact("metadata.json", "content")

    assertThat(artifacts).containsExactly("content")
  }
}
