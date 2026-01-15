package app.cash.paparazzi.plugin.module2.test

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Rule
import org.junit.Test

class ComposeDialogShrinkTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
    renderingMode = RenderingMode.SHRINK
  )

  @Test
  fun test() {
    paparazzi.snapshot {
      AlertDialog(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp)),
        onDismissRequest = {},
        title = {
          Text(
            modifier = Modifier
              .fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Title"
          )
        },
        text = {
          Text(
            modifier = Modifier
              .fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Subtitle"
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
              onClick = {}
            ) {
              Text("Button 1")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              modifier = Modifier.fillMaxWidth(),
              onClick = {}
            ) {
              Text("Button 2")
            }
          }
        }
      )
    }
  }
}
