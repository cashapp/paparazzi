package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun HelloPaparazzi() {
  val text = "Hello, Paparazzi"
  Column(Modifier
    .background(Color.White)
    .fillMaxSize()
    .wrapContentSize()) {
    Text(text)
    Text(text, style = TextStyle(fontFamily = FontFamily.Cursive))
    Text(
      text = text,
      style = TextStyle(textDecoration = TextDecoration.LineThrough)
    )
    Text(
      text = text,
      style = TextStyle(textDecoration = TextDecoration.Underline)
    )
    Text(
      text = text,
      style = TextStyle(
        textDecoration = TextDecoration.combine(
          listOf(
            TextDecoration.Underline,
            TextDecoration.LineThrough
          )
        ),
        fontWeight = FontWeight.Bold
      )
    )
  }
}
