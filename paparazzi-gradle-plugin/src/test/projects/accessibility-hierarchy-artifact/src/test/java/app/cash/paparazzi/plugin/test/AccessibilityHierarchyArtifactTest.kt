package app.cash.paparazzi.plugin.test

import android.app.Dialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK
import org.junit.Rule
import org.junit.Test

class AccessibilityHierarchyArtifactTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun record() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
      addView(TextView(context).apply { text = "First" })
      addView(TextView(context).apply { text = "Second" })
    }

    val dialog = Dialog(paparazzi.context).apply {
      setContentView(TextView(context).apply { text = "Dialog overlay" })
      show()
    }
    try {
      paparazzi.snapshot(view, name = "accessibility")
    } finally {
      dialog.dismiss()
    }
  }

  @Test
  fun recordSmall() {
    paparazzi.unsafeUpdateConfig(renderingMode = SHRINK)
    val side = (24 * paparazzi.context.resources.displayMetrics.density).toInt()
    val view = View(paparazzi.context).apply {
      layoutParams = ViewGroup.LayoutParams(side, side)
      contentDescription = "Icon"
      setBackgroundColor(Color.BLUE)
    }
    paparazzi.snapshot(view, name = "accessibility")
  }
}
