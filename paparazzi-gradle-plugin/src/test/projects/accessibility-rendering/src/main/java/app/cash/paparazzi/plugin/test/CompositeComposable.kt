package app.cash.paparazzi.plugin.test

import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Checkbox
import androidx.compose.material.IconToggleButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView

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
        .semantics { selected = true }
        .fillMaxWidth()
    ) {
      Text(
        "Option",
        Modifier
          .weight(1f)
          .semantics {
            contentDescription =
              "Custom content description for Text. This is a really long description, bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla bla. "
          }
      )
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
          Text(text = "more text", modifier = Modifier.semantics { contentDescription = "custom description" })
        }
      }
    }

    // Complex semantic layout
    Row(modifier = Modifier.clickable(onClickLabel = "On Click Label") { }) {
      Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text("Merged Text")
        Text("More Text")
      }
      Column {
        Text("Unmerged Text")
        Text("More Text")
      }
    }

    IconToggleButton(checked = true, onCheckedChange = { }) {
      Text("Toggle Button")
    }

    Text("multi\nline\ntext", modifier = Modifier.semantics { heading() })
    TextField(value = "Some text", label = { Text(text = "text field label") }, onValueChange = {})
    TextField(
      value = "Some text with error", label = { Text(text = "text field label") }, onValueChange = {}, isError = true
    )
    TextField(
      value = "TextField with custom error message",
      onValueChange = {},
      isError = true,
      modifier = Modifier.semantics {
        this.error("some error")
      }
    )

    AndroidView(
      modifier = Modifier.wrapContentSize(),
      factory = { context ->
        TextView(context).apply {
          text = "Nested Android View"
        }
      }
    )
  }
}
