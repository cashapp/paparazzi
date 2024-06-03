package app.cash.paparazzi.plugin.test

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class OverlaysTest {

  @get:Rule val paparazzi = Paparazzi(
    theme = "Theme.Cash.Default.Accent",
    deviceConfig = DeviceConfig.PIXEL.copy(fontScale = 2f),
    maxPercentDifference = 0.0
  )

  private val placeholderBackground = Color(0xFF454647)
  private val placeholderLabel = Color(0xFF747576)
  private val blue = Color(0xFF00D4FF)
  private val white = Color(0xFFFFFFFF)
  private val background = Color(0xFF171819)

  @Composable
  fun dummyPainter(
    text: String,
    background: Color = placeholderBackground.copy(alpha = 0.3f),
    textColor: Color = placeholderLabel,
    textSize: TextUnit = 6.sp,
    textOffsetX: Dp = 0.dp,
    textOffsetY: Dp = 0.dp
  ): Painter {
    return with(LocalDensity.current) {
      DummyPainter(
        text = text,
        textColor = textColor,
        background = background,
        textSize = textSize.toPx(),
        offsetX = textOffsetX.toPx(),
        offsetY = textOffsetY.toPx()
      )
    }
  }

  internal class DummyPainter(
    private val text: String,
    private val textColor: Color,
    private val background: Color,
    private val textSize: Float,
    private val offsetX: Float,
    private val offsetY: Float
  ) : Painter() {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = textColor.toArgb()
      isAntiAlias = true
      typeface = Typeface.MONOSPACE
      textSize = this@DummyPainter.textSize
    }

    // no intrinsic size allows the fill and text to match layout dimens without scaling issues
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
      fun Int.toDpString() = "${toDp().value.toInt()}dp"

      drawIntoCanvas {
        with(it.nativeCanvas) {
          drawColor(background.toArgb())
          val lineHeight = textPaint.fontSpacing
          val textOffsetX = 1.dp.toPx() + offsetX
          val textOffsetY = (size.center.y - lineHeight * 0.55f) + offsetY

          translate(left = textOffsetX, top = textOffsetY) {
            drawText(text, 0f, 0f, textPaint)
            scale(0.8f, pivot = Offset(0f, 0f)) {
              drawText("w${width.toDpString()}", 0f, lineHeight, textPaint)
              drawText("h${height.toDpString()}", 0f, lineHeight * 2, textPaint)
            }
          }
        }
      }
    }
  }

  @Test
  fun default() = paparazzi.snapshot {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(background)
    ) {
      Image(
        modifier = Modifier.requiredSize(100.dp),
        painter = dummyPainter(text = "default"),
        contentDescription = null
      )
    }
  }

  @Test
  fun `two layers`() = paparazzi.snapshot {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(background)
    ) {
      Image(
        modifier = Modifier.fillMaxSize(),
        painter = dummyPainter(text = "background"),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .padding(12.dp)
          .requiredSize(48.dp)
          .align(Alignment.TopEnd),
        painter = dummyPainter(text = "close"),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .padding(bottom = 128.dp)
          .requiredSize(128.dp)
          .padding(16.dp)
          .align(Alignment.Center),
        painter = dummyPainter(text = "feature"),
        contentDescription = null
      )
    }
  }

  @Test
  fun `four layers`() = paparazzi.snapshot {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(background)
    ) {
      Image(
        modifier = Modifier.fillMaxSize(),
        painter = dummyPainter(text = "bottom"),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .fillMaxSize()
          .padding(50.dp),
        painter = dummyPainter(text = "middle"),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .fillMaxSize()
          .padding(100.dp),
        painter = dummyPainter(text = "top"),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .requiredSize(200.dp)
          .align(Alignment.BottomEnd),
        painter = dummyPainter(text = "square"),
        contentDescription = null
      )
    }
  }

  @Test
  fun `custom options`() = paparazzi.snapshot {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(background)
    ) {
      Image(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.8f)
          .padding(27.dp),
        painter = dummyPainter(
          text = "larger",
          textSize = 16.sp,
          textOffsetY = (-64).dp,
          textOffsetX = 200.dp,
          background = blue.copy(alpha = 0.3f),
          textColor = white
        ),
        contentDescription = null
      )
      Image(
        modifier = Modifier
          .padding(start = 12.dp, bottom = 24.dp)
          .fillMaxSize(0.3f)
          .align(Alignment.CenterStart),
        painter = dummyPainter(
          text = "smaller",
          textSize = 4.sp,
          textOffsetX = 25.dp,
          textOffsetY = 10.dp,
          background = Color.Magenta.copy(alpha = 0.2f),
          textColor = Color.Red.copy(alpha = 0.3f)
        ),
        contentDescription = null
      )
    }
  }
}
