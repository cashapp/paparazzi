package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ComposeTest {
  @get:Rule
  val paparazzi = Paparazzi(maxPercentDifference = 0.0)

  @Test
  fun compose() {
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }

  @Test
  fun maxDiffRepro() {
    paparazzi.snapshot {
      Box(modifier = Modifier.background(Color(0xFFFFFFFF))) {
        Text(text = "Hello, Paparazzi!", color = Color(0xFF000000))
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0x73000000))
          .systemBarsPadding()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = Modifier
            .defaultMinSize(minWidth = 311.dp, minHeight = 200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color = Color(0xFFFFFFFF)),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = "Modal Title",
            color = Color(0xFF000000)
          )

          Text(
            text = "Modal Content",
            color = Color(0xFF000000)
          )

          Button(onClick = {}) {
            Text("Ok")
          }
        }
      }
    }
  }
}
