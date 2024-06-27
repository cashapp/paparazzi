package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class RecomposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test fun recomposesOnStateChange() {
    paparazzi.snapshot {
      var text by remember { mutableStateOf("Hello") }
      LaunchedEffect(Unit) {
        text = "Hello Paparazzi"
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.White)
      ) {
        Text(
          modifier = Modifier.align(Alignment.Center),
          text = text
        )
      }
    }
  }

  @Test
  fun customWrappingText(): Unit = paparazzi.snapshot {
    Column(modifier = Modifier.background(Color.White)) {
      CustomWrappingText(first = "abc", second = "def")
      Spacer(modifier = Modifier.size(16.dp))
      CustomWrappingText(first = "abcd", second = "very long string you should think about it, i like tacos")
    }
  }
}

@Composable
private fun CustomWrappingText(first: String, second: String) {
  var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
  var finalText by remember(first, second) { mutableStateOf("$first * $second") }
  var isSplit by remember(first, second) { mutableStateOf(false) }
  LaunchedEffect(textLayoutResult) {
    println("GOT TEXT LAYOUT RESULT ${textLayoutResult != null}")
    if (textLayoutResult != null && !isSplit && textLayoutResult!!.lineCount > 1) {
      isSplit = true
      finalText = "$first\n$second"
    }
  }
  Text(
    text = finalText,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    onTextLayout = {
      println("ON TEXT LAYOUT FIRED, SETTING VALUE")
      textLayoutResult = it
    }
  )
}
