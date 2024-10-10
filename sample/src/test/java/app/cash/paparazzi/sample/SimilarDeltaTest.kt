package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SimilarDeltaTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun composeSimilarDeltaBoxes() {
    paparazzi.snapshot { SimilarDeltaBoxes(shouldUseOriginalBackgroundColor = true) }
  }
}

@Suppress("TestFunctionName")
@Composable
fun SimilarDeltaBoxes(
  shouldUseOriginalBackgroundColor: Boolean
) {
  Column(
    Modifier
      .background(Color.White)
      .fillMaxSize()
      .wrapContentSize()
  ) {
    Box(
      Modifier
        .background(if (shouldUseOriginalBackgroundColor) Color.Yellow else Color(0xFFFFFD00))
        .size(100.dp),
      contentAlignment = Alignment.Center
    ) {
      Text("Similar")
    }

    Box(
      Modifier
        .background(if (shouldUseOriginalBackgroundColor) Color.Yellow else Color.Red)
        .size(100.dp),
      contentAlignment = Alignment.Center
    ) {
      Text("Different")
    }
  }
}
