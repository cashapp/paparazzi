package app.cash.paparazzi.plugin.test

import android.content.Context
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

class ComposeVerticalScrollSnapshotTest {
  @get:Rule
  val paparazzi =
    Paparazzi(
      deviceConfig = DeviceConfig.NEXUS_4.copy(fontScale = 2f),
      renderingMode = RenderingMode.V_SCROLL
    )

  @Test
  fun longScrollableContent() {
    val composeView =
      ComposeView(paparazzi.context).apply {
        setContent {
          // Some Compose layouts update size-dependent state after their first text measurement.
          var textMeasured by remember { mutableStateOf(false) }

          Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
              Box(
                modifier = Modifier.fillMaxWidth().height(64.dp).background(Color(0xff202124)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Fixed header",
                  color = Color.White,
                  modifier = Modifier.padding(bottom = if (textMeasured) 1.dp else 0.dp),
                  onTextLayout = { textMeasured = true }
                )
              }

              Column(
                modifier =
                Modifier
                  .fillMaxWidth()
                  .wrapContentHeight()
                  .verticalScroll(rememberScrollState())
              ) {
                repeat(20) { index ->
                  Row(
                    modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(72.dp)
                      .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier =
                      Modifier
                        .size(40.dp)
                        .background(
                          if (index % 2 == 0) Color(0xff6c4eff) else Color(0xff00a884)
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Scrollable item ${index + 1}")
                  }
                }

                Box(
                  modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Color(0xffffc043)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("End of scrollable content")
                }
              }
            }
          }
        }
      }

    paparazzi.snapshot(
      view = BoundedLayout(paparazzi.context).apply {
        addView(composeView)
      }
    )
  }

  private class BoundedLayout(context: Context) : FrameLayout(context) {
    private var handleMeasure = true
    private var measuredHeightAtDraw = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      val widthSpec =
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
          MeasureSpec.makeMeasureSpec(MAX_DIMENSION, MeasureSpec.AT_MOST)
        } else {
          widthMeasureSpec
        }

      val heightSpec =
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
          if (handleMeasure) {
            MeasureSpec.makeMeasureSpec(MAX_DIMENSION, MeasureSpec.AT_MOST)
          } else {
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
          }
        } else if (!handleMeasure) {
          MeasureSpec.makeMeasureSpec(
            min(measuredHeightAtDraw, MeasureSpec.getSize(heightMeasureSpec)),
            MeasureSpec.EXACTLY
          )
        } else {
          heightMeasureSpec
        }

      super.onMeasure(widthSpec, heightSpec)
    }

    init {
      viewTreeObserver.addOnPreDrawListener(
        object : OnPreDrawListener {
          override fun onPreDraw(): Boolean {
            handleMeasure = false
            measuredHeightAtDraw = measuredHeight
            viewTreeObserver.removeOnPreDrawListener(this)
            return true
          }
        }
      )
    }

    private companion object {
      private const val MAX_DIMENSION = 0xFFFF
    }
  }
}
