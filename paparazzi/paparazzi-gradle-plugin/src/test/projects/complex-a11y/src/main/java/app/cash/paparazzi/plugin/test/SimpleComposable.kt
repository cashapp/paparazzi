package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun SimpleComposable() {
  Column {
    Button(onClick = {}) {
      Text("On Click")
    }
    Button(onClick = {}, enabled = false) {
      Text("Disabled Button")
    }
    Button(onClick = {}) {
      Icon(Icons.Default.Add, contentDescription = "Add Item")
    }
    Checkbox(checked = true, onCheckedChange = {})
    RadioButton(selected = true, onClick = {})
    Text("Header", modifier = Modifier.semantics { heading() })
  }
}
