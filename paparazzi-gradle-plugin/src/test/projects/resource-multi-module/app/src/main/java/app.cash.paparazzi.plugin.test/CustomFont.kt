package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.cash.paparazzi.plugin.typography.GreatVibes

@Composable
fun CustomFont() {
  MaterialTheme {
    Box(Modifier.fillMaxSize().background(color = Color.White)) {
      Text(
        text = "Hello, Paparazzi!",
        style = TextStyle(
          fontFamily = GreatVibes
        )
      )
    }
  }
}
