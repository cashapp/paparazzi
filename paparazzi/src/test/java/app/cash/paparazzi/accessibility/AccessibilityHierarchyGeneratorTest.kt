package app.cash.paparazzi.accessibility

import android.widget.TextView
import app.cash.paparazzi.Flags
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AccessibilityHierarchyGeneratorTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @After
  fun clearHierarchyProperty() {
    System.clearProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED)
  }

  @Test
  fun `generate returns one structured frame from ordered window roots`() {
    System.setProperty(Flags.ACCESSIBILITY_HIERARCHY_ARTIFACTS_ENABLED, "true")
    val windowRoots = listOf(
      TextView(paparazzi.context).apply { text = "First window" },
      TextView(paparazzi.context).apply { text = "Second window" }
    )

    val frame = AccessibilityHierarchyGenerator().generate(
      windowRoots = windowRoots,
      width = 100,
      height = 200
    )

    assertThat(frame).isNotNull()
    assertThat(frame!!.elements.map { it.mainAccessibilityText })
      .containsExactly("First window", "Second window")
      .inOrder()
    assertThat(frame.width).isEqualTo(100)
    assertThat(frame.height).isEqualTo(200)
  }
}
