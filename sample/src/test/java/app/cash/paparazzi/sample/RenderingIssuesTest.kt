package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class RenderingIssuesTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun example() {
    paparazzi.snapshot {
      Box(
        modifier = Modifier.background(Color(0xFF000033))
      ) {
        Text("ExampleText", color = Color.White)
      }
    }
  }

  @Test
  fun simpleBoxAlpha() {
    paparazzi.snapshot { SimpleBoxAlphaRepro() }
  }

  @Test
  fun simpleBoxAlpha2() {
    paparazzi.snapshot { SimpleBoxAlphaRepro2() }
  }
}
