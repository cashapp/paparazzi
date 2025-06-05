package app.cash.paparazzi.sample

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.IconToggleButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class ComposeA11yTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun multiComposeViews() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
      setPaddingRelative(25, 25, 25, 25)

      addView(
        ComposeView(context = paparazzi.context).apply {
          setPaddingRelative(32, 32, 32, 32)
          setContent { Text("Number 1") }
        },
        LinearLayout.LayoutParams(WRAP_CONTENT, 0, 1f)
      )
      addView(
        ComposeView(context = paparazzi.context).apply {
          setPaddingRelative(32, 32, 32, 32)
          setContent { Text("Number 2") }
        },
        LinearLayout.LayoutParams(WRAP_CONTENT, 0, 1f)
      )
      addView(
        ComposeView(context = paparazzi.context).apply {
          setPaddingRelative(32, 32, 32, 32)
          setContent { Text("Number 3") }
        },
        LinearLayout.LayoutParams(WRAP_CONTENT, 0, 1f)
      )
    }

    paparazzi.snapshot(view)
  }

  @Test
  fun compositeItems() {
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
          Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text("Text")
            Text(
              text = "more text",
              modifier = Modifier.semantics { contentDescription = "custom description" }
            )
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
              Text("Nested text")
              Text(
                text = "more text",
                modifier = Modifier.semantics { contentDescription = "custom description" }
              )
            }
          }
        }
        TextField(value = "Some text", label = { Text(text = "text field label") }, onValueChange = {})
      }
    }
  }

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
        item {
          Button(
            modifier = Modifier
              .padding(24.dp)
              .semantics {
                selected = true
                role = Role.Switch
                heading()
              },
            onClick = {}
          ) {
            Text("07")
          }
        }
        item {
          IconToggleButton(checked = true, onCheckedChange = { }) {
            Text("Toggle")
          }
        }
      }
    }
  }
}
