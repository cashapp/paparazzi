package app.cash.paparazzi.accessibility

import android.app.Dialog
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.cash.paparazzi.Flags
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
    System.clearProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)
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
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
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
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
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

  @Test
  fun `dialog overlay hierarchy includes each render root in provided order`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add
    val dialog = Dialog(paparazzi.context).apply {
      setContentView(TextView(context).apply { text = "Dialog overlay" })
      show()
    }

    try {
      paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Primary content" })
    } finally {
      dialog.dismiss()
    }

    val hierarchy = hierarchies.single().single()
    assertThat(hierarchy).isEqualTo(
      """
      [
        {
          "id": "TextView(Dialog overlay)",
          "beforeElementId": null,
          "afterElementId": "TextView(Primary content)",
          "bounds": {
            "left": 409,
            "top": 931,
            "right": 671,
            "bottom": 988
          },
          "legendText": "Dialog overlay"
        },
        {
          "id": "TextView(Primary content)",
          "beforeElementId": "TextView(Dialog overlay)",
          "afterElementId": null,
          "bounds": {
            "left": 0,
            "top": 0,
            "right": 1080,
            "bottom": 1920
          },
          "legendText": "Primary content"
        }
      ]
      """.trimIndent()
    )
  }

  @Test
  fun `popup dialog and primary roots are each included once`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add
    val dialog = Dialog(paparazzi.context).apply {
      setContentView(TextView(context).apply { text = "Dialog overlay" })
      show()
    }
    val popup = PopupWindow(
      TextView(paparazzi.context).apply { text = "Popup overlay" },
      WRAP_CONTENT,
      WRAP_CONTENT,
      true
    ).apply {
      showAtLocation(dialog.window!!.decorView, Gravity.CENTER, 0, 0)
    }

    try {
      paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Primary content" })
    } finally {
      popup.dismiss()
      dialog.dismiss()
    }

    val hierarchy = hierarchies.single().single()
    assertThat(hierarchy.countOccurrences("\"legendText\": \"Popup overlay\"")).isEqualTo(1)
    assertThat(hierarchy.countOccurrences("\"legendText\": \"Dialog overlay\"")).isEqualTo(1)
    assertThat(hierarchy.countOccurrences("\"legendText\": \"Primary content\"")).isEqualTo(1)
    assertThat(hierarchy.indexOf("Dialog overlay")).isLessThan(hierarchy.indexOf("Popup overlay"))
    assertThat(hierarchy.indexOf("Popup overlay")).isLessThan(hierarchy.indexOf("Primary content"))
  }

  private fun accessibleView() =
    LinearLayout(paparazzi.context).apply {
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      orientation = LinearLayout.VERTICAL
      addView(TextView(context).apply { text = "First" })
      addView(TextView(context).apply { text = "Second" })
    }
}

private fun String.countOccurrences(value: String): Int = windowed(value.length).count { it == value }

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
