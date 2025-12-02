package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.Paparazzi
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PaparazziWithCoroutineTest {

  init {
    System.setProperty("paparazzi.dispatcher.strict", "true")
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    System.clearProperty("paparazzi.dispatcher.strict")
  }

  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun `given strict mode true, when paparazzi takes a snapshot, then throw an exception`() {
    Dispatchers.setMain(StandardTestDispatcher())

    assertThrows(IllegalStateException::class.java) {
      paparazzi.snapshot {
        HelloWorldContent()
      }
    }
  }

  @Test
  fun `when strict mode is false then no exception thrown snapshot`() {
    paparazzi.snapshot {
      HelloWorldContent()
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

  @Test
  fun `when strict mode is true and dispatcher reset, then no exception thrown`() {
    Dispatchers.setMain(StandardTestDispatcher())
    Dispatchers.resetMain()

    paparazzi.snapshot {
      HelloWorldContent()
    }
  }

  @Composable
  fun HelloWorldContent() {
    Column(
      modifier = Modifier.fillMaxSize().background(Color.DarkGray),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      repeat(5) {
        Text(text = "Hello Main Dispatcher Validator!")
      }
    }
  }
}
