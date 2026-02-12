package app.cash.paparazzi.plugin.test

import android.app.Dialog
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
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
}
