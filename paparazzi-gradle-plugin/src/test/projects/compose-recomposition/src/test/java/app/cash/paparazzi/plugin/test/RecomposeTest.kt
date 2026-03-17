package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
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

  @Test fun recomposesOnTextLayout() {
    paparazzi.snapshot {
      MaterialTheme {
        var lineCount by remember { mutableStateOf(0) }

        Column(
          modifier = Modifier.background(Color.White),
          verticalArrangement = spacedBy(8.dp)
        ) {
          Text("Text Line count: $lineCount")
          Text(
            "Sample text",
            onTextLayout = {
              lineCount = it.lineCount
            }
          )
        }
      }
    }
  }

  @Test fun recomposesOnGlobalPositioning() {
    paparazzi.snapshot {
      MaterialTheme {
        var globalPosition by remember { mutableStateOf(Offset.Zero) }
        Column(Modifier.background(Color.White)) {
          Text(text = "Global Position: $globalPosition")

          Spacer(
            modifier = Modifier
              .size(100.dp)
              .background(Color.Green)
              .align(Alignment.CenterHorizontally)
              .onGloballyPositioned {
                globalPosition = it.localToRoot(Offset.Zero)
              }
          )
        }
      }
    }
  }

  @Test fun imageHasCorrectSizeAfterRecomposition() {
    paparazzi.unsafeUpdateConfig(renderingMode = SessionParams.RenderingMode.FULL_EXPAND)
    paparazzi.snapshot {
      var size by remember { mutableStateOf(IntSize.Zero) }
      Box(
        modifier = Modifier
          .size(with(LocalDensity.current) { 3000.toDp() })
          .background(Color.White)
          .onSizeChanged { size = it }
      ) {
        BasicText(
          text = "Size: $size",
          modifier = Modifier.align(Alignment.Center)
        )
      }
    }
  }
}
