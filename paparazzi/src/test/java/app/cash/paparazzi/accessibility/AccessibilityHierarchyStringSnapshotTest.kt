package app.cash.paparazzi.accessibility

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class AccessibilityHierarchyStringSnapshotTest {
  private val events = mutableListOf<String>()

  @get:Rule
  val paparazzi = Paparazzi(
    snapshotHandler = InMemorySnapshotHandler(events)
  )

  @After
  fun clearHierarchyProperty() {
    System.clearProperty(ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)
  }

  @Test
  fun `accessibility hierarchy generation is disabled by default`() {
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    paparazzi.snapshot(accessibleView(), name = "default-off")

    assertThat(hierarchies).isEmpty()
  }

  @Test
  fun `gradle property enables accessibility hierarchy generation after each snapshot run`() {
    System.setProperty(ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    paparazzi.snapshot(accessibleView(), name = "hierarchy-1")
    paparazzi.snapshot(accessibleView(), name = "hierarchy-2")

    assertThat(hierarchies).hasSize(2)
    hierarchies.forEach { frames ->
      assertThat(frames).hasSize(1)
      assertThat(frames.single()).contains("\"legendText\": \"First\"")
      assertThat(frames.single()).contains("\"legendText\": \"Second\"")
    }
  }

  @Test
  fun `hierarchy is delivered after frame verification failure and before handler cleanup`() {
    System.setProperty(ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    events += "snapshot started"
    paparazzi.onAccessibilityHierarchiesGenerated = { events += "hierarchy generated" }

    assertThrows(AssertionError::class.java) {
      paparazzi.snapshot(accessibleView(), name = "failing-verification")
    }

    assertThat(events).containsExactly(
      "snapshot started",
      "frame handled",
      "hierarchy generated",
      "handler closed"
    ).inOrder()
  }

  private fun accessibleView() =
    LinearLayout(paparazzi.context).apply {
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      orientation = LinearLayout.VERTICAL
      addView(TextView(context).apply { text = "First" })
      addView(TextView(context).apply { text = "Second" })
    }
}

private class InMemorySnapshotHandler(
  private val events: MutableList<String>
) : SnapshotHandler {
  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
    return object : SnapshotHandler.FrameHandler {
      override fun handle(image: BufferedImage) {
        events += "frame handled"
        if (snapshot.name == "failing-verification") {
          throw AssertionError("frame verification failed")
        }
      }

      override fun close() {
        events += "handler closed"
      }
    }
  }

  override fun close() = Unit
}
