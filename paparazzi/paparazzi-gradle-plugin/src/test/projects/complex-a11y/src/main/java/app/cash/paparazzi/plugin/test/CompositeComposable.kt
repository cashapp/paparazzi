package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics

@Composable
fun CompositeComposable() {
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
