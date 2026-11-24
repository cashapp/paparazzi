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

class RecordSnapshotTest {
  @get:Rule
  val paparazzi = Paparazzi(
    renderingMode = SessionParams.RenderingMode.SHRINK,
    maxPercentDifference = 20.0
  )

  @Test
  fun dontRecord() {
    paparazzi.snapshot {
      Spacer(
        modifier = Modifier
          .width(1.dp)
          .height(4.dp)
          .background(Color.Red.copy(alpha = 0.5f))
      )
    }
  }

  @Test
  fun record() {
    paparazzi.snapshot {
      Spacer(
        modifier = Modifier
          .width(40.dp)
          .height(40.dp)
          .background(Color.Blue)
      )
    }
  }
}
