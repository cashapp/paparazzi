package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.cash.paparazzi.Paparazzi
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class HelloComposeTest {

  init {
    System.setProperty("paparazzi.dispatcher.strict", "true")
  }

  @get:Rule
  val paparazzi = Paparazzi()

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    System.setProperty("paparazzi.dispatcher.strict", "false")
  }

  @Test
  fun compose() {
    paparazzi.snapshot { HelloPaparazzi() }
  }

  @Test
  fun `given strict mode true, when paparazzi takes a snapshot, then throw an exception`() {
    Dispatchers.setMain(StandardTestDispatcher())

    assertThrows(IllegalStateException::class.java) {
      paparazzi.snapshot { HelloPaparazzi() }
    }
  }

  @Test
  fun `when strict mode is false then no exception thrown snapshot`() {
      paparazzi.snapshot {
        LaunchedEffect(key1 = Unit) { }
        Column { }
      }
  }

  @Test
  fun `given strict mode true, when paparazzi takes a gif, then throw an exception`() {
    Dispatchers.setMain(StandardTestDispatcher())

    assertThrows(IllegalStateException::class.java) {
      paparazzi.gif(ComposeView(paparazzi.context), end = 100L)
    }
  }

  @Test
  fun `when strict mode is false then no exception thrown gif`() {
    paparazzi.gif(ComposeView(paparazzi.context), end = 100L)
  }
}

@Suppress("TestFunctionName")
@Composable
fun HelloPaparazzi() {
  val text = "Hello, Paparazzi"
  Column(
    Modifier
      .background(Color.White)
      .fillMaxSize()
      .wrapContentSize()
  ) {
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
