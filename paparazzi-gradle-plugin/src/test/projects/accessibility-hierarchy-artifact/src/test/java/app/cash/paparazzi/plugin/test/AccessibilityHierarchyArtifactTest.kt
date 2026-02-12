package app.cash.paparazzi.plugin.test

import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class AccessibilityHierarchyArtifactTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun record() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
      addView(TextView(context).apply { text = "First" })
      addView(TextView(context).apply { text = "Second" })
    }

    paparazzi.snapshot(view, name = "accessibility")
  }
}
