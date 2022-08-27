package app.cash.paparazzi.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Paparazzi.Companion.determineHandler
import app.cash.paparazzi.accessibility.A11ySnapshotHandler
import app.cash.paparazzi.accessibility.ComposeA11yExtension
import org.junit.Rule
import org.junit.Test

class ComposeA11yTest {
  private val composeA11yExtension = ComposeA11yExtension()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(composeA11yExtension),
    maxPercentDifference = 0.1,
    snapshotHandler = A11ySnapshotHandler(
      delegate = determineHandler(maxPercentDifference = 0.1),
      accessibilityStateFn = { composeA11yExtension.accessibilityState }
    )
  )

  @Test
  fun SimpleItems() {
    paparazzi.snapshot {
      Column {
        Button(onClick = {}) {
          Text("On Click")
        }
        Button(onClick = {}, enabled = false) {
          Text("Disabled Button")
        }
        Button(onClick = {}) {
          Icon(Icons.Default.Add, "Add Item")
        }
        Checkbox(checked = true, onCheckedChange = {})
        RadioButton(selected = true, onClick = {})
        Text("Header", modifier = Modifier.semantics { heading() })
      }
    }
  }

  @Test
  fun CompositeItems() {
    paparazzi.snapshot {
      Column {
        Row(
          Modifier
            .toggleable(
              value = true,
              role = Role.Checkbox,
              onValueChange = { }
            )
            .fillMaxWidth()
        ) {
          Text("Option", Modifier.weight(1f))
          Checkbox(checked = true, onCheckedChange = null)
        }
        Box(
          Modifier
            .align(Alignment.CenterHorizontally)
            .clickable(onClickLabel = "On Click Label") { }
        )
        Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
          Image(
            imageVector = Icons.Filled.Add,
            contentDescription = null // decorative
          )
          Column {
            Text("Text")
            Text("more text")
          }
        }
      }
    }
  }
}
