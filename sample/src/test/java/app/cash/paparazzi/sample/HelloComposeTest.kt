package app.cash.paparazzi.sample

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class HelloComposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun compose() {
    paparazzi.snapshot { HelloPaparazzi() }
  }
}

@Suppress("TestFunctionName")
@Composable
fun HelloPaparazzi() {
  AndroidView(
    factory = { context ->
      CustomButton(
        ContextThemeWrapper(context, R.style.CustomTheme),
      )
    },
    update = { button ->
      with(button) {
        text = "Hello Paparazzi"
      }
    }
  )
}
