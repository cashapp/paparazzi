package app.cash.paparazzi.accessibility

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.state.ToggleableState
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class AccessibilityElementCollectorTest {
  @get:Rule
  val paparazzi = Paparazzi(
    snapshotHandler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
        object : SnapshotHandler.FrameHandler {
          override fun handle(image: BufferedImage) = Unit
          override fun close() = Unit
        }
      override fun close() = Unit
    }
  )

  @Test
  fun `merged controls retain structured metadata and legend order`() {
    var element: AccessibilityElement? = null
    paparazzi.snapshot {
      val root = LocalView.current as ViewRootForTest
      SemanticsLayout(
        Modifier.drawWithContent {
          drawContent()
          val owner = root.semanticsOwner
          val node = owner.getAllSemanticsNodes(true).single {
            it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox
          }
          element =
            AccessibilityElement.fromSemanticsNode(node, Rect(0, 0, 100, 100), owner.getAllSemanticsNodes(false))
        }.semantics(mergeDescendants = true) {
          role = Role.Checkbox
          stateDescription = "Unavailable"
          toggleableState = ToggleableState.On
          disabled()
        }
      ) {
        SemanticsLayout(Modifier.semantics { contentDescription = "Notifications" })
        SemanticsLayout(Modifier.semantics { contentDescription = "   " })
        SemanticsLayout(Modifier.semantics { contentDescription = "Alerts" })
        SemanticsLayout(
          Modifier.alpha(0f).semantics {
            contentDescription = "Transparent label"
            heading()
          }
        )
        SemanticsLayout(
          Modifier.semantics {
            contentDescription = "Hidden label"
            hideFromAccessibility()
          }
        )
      }
    }

    val collected = requireNotNull(element)
    assertThat(collected.mainAccessibilityText).isEqualTo("Notifications, Alerts")
    assertThat(collected.role).isEqualTo("Checkbox")
    assertThat(collected.stateDescription).isEqualTo("Unavailable")
    assertThat(collected.toggleableState).isEqualTo("<toggleable>: checked")
    assertThat(collected.disabled).isEqualTo("<disabled>")
    assertThat(collected.heading).isNull()
    assertThat(collected.legendText)
      .isEqualTo("Notifications, Alerts, Unavailable, <toggleable>: checked, Checkbox, <disabled>")
  }

  @Test
  fun `merged nodes with only filtered labels are ignored`() {
    var collected = false
    var element: AccessibilityElement? = null
    paparazzi.snapshot {
      val root = LocalView.current as ViewRootForTest
      SemanticsLayout(
        Modifier.drawWithContent {
          drawContent()
          val owner = root.semanticsOwner
          val node = owner.getAllSemanticsNodes(true).single { it.config.isMergingSemanticsOfDescendants }
          element = AccessibilityElement.fromSemanticsNode(node, Rect(), owner.getAllSemanticsNodes(false))
          collected = true
        }.semantics(mergeDescendants = true) {}
      ) {
        SemanticsLayout(Modifier.semantics { contentDescription = "   " })
        SemanticsLayout(Modifier.alpha(0f).semantics { contentDescription = "Transparent label" })
      }
    }

    assertThat(collected).isTrue()
    assertThat(element).isNull()
  }

  @Test
  fun `indexed subtree appears once and preserves descendant order`() {
    var labels: List<String>? = null
    var nodeIds: List<Int>? = null
    paparazzi.snapshot {
      val root = LocalView.current as ViewRootForTest
      SemanticsLayout(
        Modifier.drawWithContent {
          drawContent()
          val nodes = with(AccessibilityElementCollector()) {
            root.semanticsOwner.rootSemanticsNode.orderSemanticsNodeGroup()
          }
          nodeIds = nodes.map { it.id }
          labels = nodes.mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() }
        }
      ) {
        SemanticsLayout(
          Modifier.semantics {
            contentDescription = "Last"
            traversalIndex = 1f
          }
        )
        SemanticsLayout(Modifier.semantics { traversalIndex = -1f }) {
          SemanticsLayout(Modifier.semantics { contentDescription = "First" })
          SemanticsLayout(Modifier.semantics { contentDescription = "Second" })
        }
        SemanticsLayout(Modifier.semantics { contentDescription = "Third" })
      }
    }

    assertThat(requireNotNull(labels)).containsExactly("First", "Second", "Third", "Last").inOrder()
    assertThat(requireNotNull(nodeIds)).containsNoDuplicates()
  }

  @Composable
  private fun SemanticsLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
      val children = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
      layout(100, 100) {
        children.forEach { it.place(0, 0) }
      }
    }
  }
}
