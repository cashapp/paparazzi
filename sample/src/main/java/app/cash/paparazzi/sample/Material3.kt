package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun DropDown() {
  MaterialTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White)
        .padding(8.dp)
    ) {
      var expanded by remember {
        mutableStateOf(true)
      }
      Button({ expanded = !expanded }) {
        Text("Open Dropdown")
      }

      Box {
        Text(
          text = "Label 1"
        )

        DropdownMenu(
          expanded = expanded,
          onDismissRequest = { expanded = !expanded }
        ) {
          DropdownMenuItem(
            text = {
              Text(
                text = "Label 1"
              )
            },
            onClick = {}
          )
          DropdownMenuItem(
            text = {
              Text(
                text = "Label 2"
              )
            },
            onClick = {}
          )
          DropdownMenuItem(
            text = {
              Text(
                text = "Label 3"
              )
            },
            onClick = {}
          )
        }
      }
    }
  }
}

@Composable
@Preview
fun T() {
  Box(Modifier.fillMaxSize()) {
    DropdownMenu(
      expanded = true,
      onDismissRequest = { }
    ) {
      DropdownMenuItem(
        text = {
          Text(
            text = "Label 1"
          )
        },
        onClick = {}
      )
      DropdownMenuItem(
        text = {
          Text(
            text = "Label 2"
          )
        },
        onClick = {}
      )
      DropdownMenuItem(
        text = {
          Text(
            text = "Label 3"
          )
        },
        onClick = {}
      )
    }
  }
}
