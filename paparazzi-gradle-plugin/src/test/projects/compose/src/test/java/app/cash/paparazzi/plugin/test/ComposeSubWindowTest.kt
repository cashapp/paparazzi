package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ComposeSubWindowTest {
  @get:Rule
  val paparazzi = Paparazzi()

  /**
   * A Dialog subcomposed during measure. Its window is only created if the measure pass runs, so
   * this fails outright, rendering bare background, if a traversal is skipped between attaching
   * content and the first render.
   */
  @Test
  fun dialogSubcomposedDuringMeasure() {
    paparazzi.snapshot {
      SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val placeables = subcompose(Unit) {
          Box(Modifier.fillMaxSize().background(Color.Cyan))
          Dialog(onDismissRequest = {}) {
            Box(Modifier.background(Color.Red).padding(48.dp)) { Text("DIALOG") }
          }
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
          placeables.forEach { it.place(0, 0) }
        }
      }
    }
  }
}
