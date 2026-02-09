package app.cash.paparazzi.accessibility

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class AccessibilityHierarchyStringSnapshotTest {
  private val hierarchyStrings = mutableListOf<String>()
  private val renderExtension = AccessibilityRenderExtension(
    accessibilityElementCollector = AccessibilityElementCollector(),
    onHierarchyStringGenerated = hierarchyStrings::add
  )

  @get:Rule
  val paparazzi = Paparazzi(
    snapshotHandler = InMemorySnapshotHandler(),
    renderExtensions = setOf(renderExtension)
  )

  @Test
  fun `generates accessibility hierarchy string after each snapshot run`() {
    val view = LinearLayout(paparazzi.context).apply {
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      orientation = LinearLayout.VERTICAL
      addView(TextView(context).apply { text = "First" })
      addView(TextView(context).apply { text = "Second" })
    }

    paparazzi.snapshot(view, name = "hierarchy-1")
    paparazzi.snapshot(view, name = "hierarchy-2")

    assertThat(hierarchyStrings).hasSize(2)

    val hierarchy = hierarchyStrings.first()
    assertThat(hierarchy).contains("\"legendText\": \"First\"")
    assertThat(hierarchy).contains("\"legendText\": \"Second\"")
    assertThat(hierarchy.indexOf("\"legendText\": \"First\"")).isLessThan(
      hierarchy.indexOf("\"legendText\": \"Second\"")
    )
    assertThat(hierarchy).contains("\"beforeElementId\": \"TextView(First)\"")
    assertThat(hierarchy).contains("\"afterElementId\": \"TextView(Second)\"")
  }
}

private class InMemorySnapshotHandler : SnapshotHandler {
  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
    return object : SnapshotHandler.FrameHandler {
      override fun handle(image: BufferedImage) = Unit

      override fun close() = Unit
    }
  }

  override fun close() = Unit
}
