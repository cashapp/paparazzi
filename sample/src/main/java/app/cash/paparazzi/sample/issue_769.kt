package app.cash.paparazzi.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ComposeDialog(
  modifier: Modifier = Modifier,
  title: String,
  subtitle: String,
  openDialogState: MutableState<Boolean> = remember { mutableStateOf(false) },
  isCancelable: Boolean = true,
  onDismissRequest: () -> Unit = {
    if (isCancelable) {
      openDialogState.value = false
    }
  },
) {
  AlertDialog(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp)),
    onDismissRequest = onDismissRequest,
    title = {
      Text(
        modifier = Modifier
          .fillMaxWidth(),
        textAlign = TextAlign.Center,
        text = title
      )
    },
    text = {
      Text(
        modifier = Modifier
          .fillMaxWidth(),
        textAlign = TextAlign.Center,
        text = subtitle
      )
    },
    buttons = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp
          )
      ) {
        Button(
          modifier = Modifier.fillMaxWidth(),
          onClick = {},
        ) {
          Text("test")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          modifier = Modifier.fillMaxWidth(),
          onClick = {},
        ) {
          Text("test")
        }
      }
    }
  )
}

@Preview
@Composable
fun ComposeDialogPreview() {
  ComposeDialog(
    title = "Title",
    subtitle = "Subtitle"
  )
}
