package app.cash.paparazzi.plugin.test

import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class AccessibilityArtifactsTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun snapshot() {
    assertEquals("true", System.getProperty("app.cash.paparazzi.accessibility.hierarchy.artifacts.enabled"))
    assertThrows(ClassNotFoundException::class.java) {
      Class.forName("androidx.compose.ui.platform.AbstractComposeView")
    }
    assertThrows(ClassNotFoundException::class.java) {
      Class.forName("androidx.compose.runtime.Composer")
    }

    val view = LinearLayout(paparazzi.context).apply {
      addView(TextView(context).apply { text = "Views-only accessibility" })
    }
    paparazzi.snapshot(view)
  }
}
