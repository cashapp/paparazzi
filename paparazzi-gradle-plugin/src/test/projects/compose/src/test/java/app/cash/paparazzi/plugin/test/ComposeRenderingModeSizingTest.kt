package app.cash.paparazzi.plugin.test

import android.content.Context
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import kotlin.math.min

private const val EXPECTED_CURRENT_V_SCROLL_WIDTH = 514
private const val EXPECTED_CURRENT_V_SCROLL_HEIGHT = 1000
private const val CONTENT_HEIGHT_DP = 700

class ComposeRenderingModeSizingTest {
  private val snapshotHandler = DimensionAssertingSnapshotHandler()

  @get:Rule
  val paparazzi =
    Paparazzi(
      deviceConfig = DeviceConfig.NEXUS_5,
      renderingMode = RenderingMode.V_SCROLL,
      snapshotHandler = snapshotHandler
    )

  @Test
  fun verticalScrollBoundedComposeRootKeepsCurrentSnapshotSize() {
    snapshotHandler.expect(
      name = "compose_v_scroll",
      width = EXPECTED_CURRENT_V_SCROLL_WIDTH,
      height = EXPECTED_CURRENT_V_SCROLL_HEIGHT
    )

    val composeView =
      ComposeView(paparazzi.context).apply {
        layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        setContent {
          Column(
            modifier =
            Modifier
              .fillMaxWidth()
              .height(CONTENT_HEIGHT_DP.dp)
              .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier =
              Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Red)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("bottom edge")
            Box(
              modifier =
              Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Blue)
            )
          }
        }
      }

    paparazzi.snapshot(
      view = BoundedLayout(paparazzi.context).apply {
        layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        addView(composeView)
      },
      name = "compose_v_scroll"
    )
  }

  private class DimensionAssertingSnapshotHandler : SnapshotHandler {
    private val expectedDimensions = mutableMapOf<String, Pair<Int, Int>>()

    fun expect(name: String, width: Int, height: Int) {
      expectedDimensions[name] = width to height
    }

    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
      val name = requireNotNull(snapshot.name)
      val expected = requireNotNull(expectedDimensions[name]) {
        "No expected dimensions registered for snapshot '$name'"
      }

      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          assertEquals("Snapshot width for '$name'", expected.first, image.width)
          assertEquals("Snapshot height for '$name'", expected.second, image.height)
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
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
