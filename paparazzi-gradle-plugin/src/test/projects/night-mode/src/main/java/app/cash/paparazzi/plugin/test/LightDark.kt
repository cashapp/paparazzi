package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun LightDark() {
  val darkTheme = isSystemInDarkTheme()
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(if (darkTheme) Color.Black else Color.White),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = if (darkTheme) "Dark theme" else "Light theme",
      fontSize = 20.sp,
      color = if (darkTheme) Color.White else Color.Black
    )
  }
}
