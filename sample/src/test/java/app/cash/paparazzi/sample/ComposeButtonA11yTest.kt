package app.cash.paparazzi.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class ComposeButtonA11yTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun buttonStates() {
    paparazzi.snapshot {
      LazyVerticalGrid(columns = GridCells.Fixed(2)) {
        item {
          Button(
            modifier = Modifier.padding(24.dp),
            onClick = {}
          ) {
            Text("01")
          }
        }
        item {
          Button(
            modifier = Modifier
              .padding(24.dp),
            onClick = {},
            enabled = false
          ) {
            Text("02")
          }
        }
        item {
          Button(
            modifier = Modifier
              .padding(24.dp)
              .clickable(onClickLabel = "Explore") {},
            onClick = {}
          ) {
            Text("03")
          }
        }
        item {
          Button(
            modifier = Modifier
              .padding(24.dp)
              .semantics { stateDescription = "State" },
            onClick = {}
          ) {
            Text("04")
          }
        }
        item {
          Button(
            modifier = Modifier
              .padding(24.dp)
              .semantics { role = Role.DropdownList },
            onClick = {}
          ) {
            Text("05")
          }
        }
        item {
          Button(
            modifier = Modifier
              .padding(24.dp)
              .clickable(onClickLabel = "Explore") {}
              .semantics {
                stateDescription = "State"
                role = Role.RadioButton
              },
            onClick = {},
            enabled = false
          ) {
            Text("06")
          }
        }
      }
    }
  }
}
