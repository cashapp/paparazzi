package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

class HashTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun verticalLineComponent() {
    paparazzi.snapshot {
      Spacer(
        modifier = Modifier
          .width(1.dp)
          .height(4.dp)
          .background(Color.Red)
      )
    }
  }

  @Test
  fun horizontalLineComponent() {
    paparazzi.snapshot {
      Spacer(
        modifier = Modifier
          .width(4.dp)
          .height(1.dp)
          .background(Color.Red)
      )
    }
  }

  @Test
  fun squareComponent() {
    paparazzi.snapshot {
      Spacer(
        modifier = Modifier
          .width(2.dp)
          .height(2.dp)
          .background(Color.Red)
      )
    }
  }
}
