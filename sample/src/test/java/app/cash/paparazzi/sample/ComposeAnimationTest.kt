package app.cash.paparazzi.sample

import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.LastFrameSnapshotHandler
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * This sample tests the use of compose animations (animateContentSize) and ensures that the content
 * correctly renders in the right place. To do this, we add the view to a container where it only
 * fills 50% of the space using a weight. The remaining 50% is filled with an empty box.
 */
class ComposeAnimationTest {
  @get:Rule val instantAnimationsRule = InstantAnimationsRule()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL.copy(
      screenWidth = DeviceConfig.PIXEL.screenWidth * 2,
      softButtons = false
    ),
    snapshotHandler = LastFrameSnapshotHandler()
  )

  @Test
  fun compositeItems() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

      addView(
        ComposeView(paparazzi.context).apply {
          setContent {
            Column(
              modifier = Modifier
                .animateContentSize()
                .border(
                  width = 2.dp,
                  color = Color.Cyan,
                  shape = RoundedCornerShape(2.dp)
                )
            ) {
              Row(modifier = Modifier.fillMaxWidth()) {
                Text("This is a row of text", Modifier.weight(1f))
              }
              Row(modifier = Modifier.fillMaxWidth()) {
                Text("This is some more text")
                Text(text = "And even more text")
              }
            }
          }
        },
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1f)
      )

      addView(
        LinearLayout(paparazzi.context).apply {
          setBackgroundColor(Color.Blue.toArgb())
        },
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1f)
      )
    }

    paparazzi.gif(view = view, fps = 2)
  }
}
