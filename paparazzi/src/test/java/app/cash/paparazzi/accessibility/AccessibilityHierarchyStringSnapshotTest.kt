package app.cash.paparazzi.accessibility

import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.cash.paparazzi.ArtifactFrameHandler
import app.cash.paparazzi.Flags
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.image.BufferedImage

class AccessibilityHierarchyStringSnapshotTest {
  private val events = mutableListOf<String>()
  private val snapshotHandler = InMemorySnapshotHandler(events)

  @get:Rule
  val paparazzi = Paparazzi(snapshotHandler = snapshotHandler)

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
    assertThat(snapshotHandler.artifacts).isEmpty()
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
  fun `publishes accessibility hierarchy artifact when property enabled`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")

    paparazzi.snapshot(accessibleView(), name = "hierarchy-artifact")

    assertThat(snapshotHandler.artifacts).hasSize(1)
    val artifact = snapshotHandler.artifacts.single()
    assertThat(artifact.name).isEqualTo(ACCESSIBILITY_HIERARCHY_ARTIFACT_NAME)
    assertThat(artifact.content).contains("\"legendText\": \"First\"")
    assertThat(artifact.content).contains("\"legendText\": \"Second\"")
    val frames = artifact.content.artifactFrames()
    assertThat(frames).hasSize(1)
    assertFrameDimensions(frames.single(), snapshotHandler.images.single())
  }

  @Test
  fun `user hierarchy callback does not replace artifact delivery`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    paparazzi.snapshot(accessibleView(), name = "composed-callback")

    assertThat(hierarchies).hasSize(1)
    assertThat(snapshotHandler.artifacts).hasSize(1)
  }

  @Test
  fun `user hierarchy callback runs when artifact delivery fails`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    snapshotHandler.artifactFailure = AssertionError("artifact verification failed")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    val failure = assertThrows(AssertionError::class.java) {
      paparazzi.snapshot(accessibleView(), name = "failing-artifact")
    }

    assertThat(failure).hasMessageThat().isEqualTo("artifact verification failed")
    assertThat(hierarchies).hasSize(1)
  }

  @Test
  fun `artifact failure remains primary when user hierarchy callback also fails`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    snapshotHandler.artifactFailure = AssertionError("artifact verification failed")
    paparazzi.onAccessibilityHierarchiesGenerated = {
      throw IllegalStateException("reporting failed")
    }

    val failure = assertThrows(AssertionError::class.java) {
      paparazzi.snapshot(accessibleView(), name = "two-completion-failures")
    }

    assertThat(failure).hasMessageThat().isEqualTo("artifact verification failed")
    assertThat(failure.suppressed.single()).hasMessageThat().isEqualTo("reporting failed")
  }

  @Test
  fun `gif skips accessibility hierarchy generation while capturing every image frame`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    paparazzi.gif(accessibleView(), name = "hierarchy-gif", start = 0L, end = 500L, fps = 4)

    assertThat(snapshotHandler.images).hasSize(3)
    assertThat(hierarchies).isEmpty()
    assertThat(snapshotHandler.artifacts).isEmpty()
  }

  @Test
  fun `gif with one frame also skips accessibility hierarchy generation`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    paparazzi.gif(accessibleView(), name = "single-frame-gif", start = 0L, end = 0L, fps = 4)

    assertThat(snapshotHandler.images).hasSize(1)
    assertThat(hierarchies).isEmpty()
    assertThat(snapshotHandler.artifacts).isEmpty()
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
      {
        "renderWidth": 1080,
        "renderHeight": 1920,
        "imageWidth": 562,
        "imageHeight": 1000,
        "elements": [
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
      }
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

  private fun assertFrameDimensions(frame: Map<*, *>, image: BufferedImage) {
    assertThat(frame["renderWidth"]).isEqualTo(1080.0)
    assertThat(frame["renderHeight"]).isEqualTo(1920.0)
    assertThat(frame["imageWidth"]).isEqualTo(image.width.toDouble())
    assertThat(frame["imageHeight"]).isEqualTo(image.height.toDouble())
    assertThat(image.height).isLessThan(1920)
  }

  private fun accessibleView() =
    LinearLayout(paparazzi.context).apply {
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      orientation = LinearLayout.VERTICAL
      addView(TextView(context).apply { text = "First" })
      addView(TextView(context).apply { text = "Second" })
    }
}

@RunWith(Parameterized::class)
class AccessibilityHierarchyImageBoundsTest(private val useDeviceResolution: Boolean) {
  private val snapshotHandler = InMemorySnapshotHandler(mutableListOf())

  @get:Rule
  val paparazzi = Paparazzi(
    useDeviceResolution = useDeviceResolution,
    snapshotHandler = snapshotHandler
  )

  @After
  fun clearHierarchyProperty() {
    System.clearProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)
  }

  @Test
  fun `artifact bounds map to the captured image pixels`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val view = FrameLayout(paparazzi.context).apply {
      setBackgroundColor(Color.WHITE)
      addView(
        View(context).apply {
          contentDescription = "Target"
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          setBackgroundColor(Color.RED)
        },
        FrameLayout.LayoutParams(201, 303).apply {
          leftMargin = 121
          topMargin = 157
        }
      )
    }

    paparazzi.snapshot(view)

    val frame = snapshotHandler.artifacts.single().content.artifactFrames().single()
    val image = snapshotHandler.images.single()
    assertThat(frame["imageWidth"]).isEqualTo(image.width.toDouble())
    assertThat(frame["imageHeight"]).isEqualTo(image.height.toDouble())
    assertThat(image.height).isEqualTo(if (useDeviceResolution) 1920 else 1000)
    val element = (frame["elements"] as List<*>).single() as Map<*, *>
    val bounds = element["bounds"] as Map<*, *>
    assertThat(bounds).containsExactly("left", 121.0, "top", 157.0, "right", 322.0, "bottom", 460.0)
    val scaleX = (frame["imageWidth"] as Double) / (frame["renderWidth"] as Double)
    val scaleY = (frame["imageHeight"] as Double) / (frame["renderHeight"] as Double)
    var left = image.width
    var top = image.height
    var right = 0
    var bottom = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        if (image.getRGB(x, y) == Color.RED) {
          left = minOf(left, x)
          top = minOf(top, y)
          right = maxOf(right, x + 1)
          bottom = maxOf(bottom, y + 1)
        }
      }
    }
    // Resampling can blend the edge pixels; the saved dimensions must still locate all four edges.
    assertThat(left.toDouble()).isWithin(2.0).of((bounds["left"] as Double) * scaleX)
    assertThat(top.toDouble()).isWithin(2.0).of((bounds["top"] as Double) * scaleY)
    assertThat(right.toDouble()).isWithin(2.0).of((bounds["right"] as Double) * scaleX)
    assertThat(bottom.toDouble()).isWithin(2.0).of((bounds["bottom"] as Double) * scaleY)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useDeviceResolution={0}")
    fun parameters() = listOf(false, true)
  }
}

class AccessibilityHierarchyWithRenderExtensionSnapshotTest {
  private val events = mutableListOf<String>()

  @get:Rule
  val paparazzi = Paparazzi(
    renderExtensions = setOf(AccessibilityRenderExtension()),
    snapshotHandler = InMemorySnapshotHandler(events)
  )

  @After
  fun clearHierarchyProperty() {
    System.clearProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)
  }

  @Test
  fun `accessibility render extension skips hierarchy generation even when enabled`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val hierarchies = mutableListOf<List<String>>()
    paparazzi.onAccessibilityHierarchiesGenerated = hierarchies::add

    paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Accessible content" })

    assertThat(events).containsExactly("frame handled", "handler closed").inOrder()
    assertThat(hierarchies).isEmpty()
  }
}

private fun String.countOccurrences(value: String): Int = windowed(value.length).count { it == value }

private class InMemorySnapshotHandler(
  private val events: MutableList<String>
) : SnapshotHandler {
  val artifacts = mutableListOf<Artifact>()
  val images = mutableListOf<BufferedImage>()
  var artifactFailure: Throwable? = null

  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
    return object : SnapshotHandler.FrameHandler, ArtifactFrameHandler {
      override fun handle(image: BufferedImage) {
        events += "frame handled"
        images += image
        if (snapshot.name == "failing-verification") {
          throw AssertionError("frame verification failed")
        }
      }

      override fun handleArtifact(name: String, content: String) {
        artifactFailure?.let { throw it }
        artifacts += Artifact(name, content)
      }

      override fun close() {
        events += "handler closed"
      }
    }
  }

  override fun close() = Unit
}

private data class Artifact(
  val name: String,
  val content: String
)

private fun String.artifactFrames(): List<Map<*, *>> {
  val artifact = Moshi.Builder().build().adapter(Map::class.java).fromJson(this)!!
  return (artifact["frames"] as List<*>).map { it as Map<*, *> }
}
