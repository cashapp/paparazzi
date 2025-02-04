package app.cash.paparazzi.sample

import android.annotation.SuppressLint
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

private val fontResource = R.font.overshoot_test
private val regularExtentChar = "a" // width of 1, and a matching extent of 1
private val largerExtentChar = "b" // width of 1, but a larger extent of 1.5
private val lineHeight = 20.sp // lineHeight will force the text to use StaticLayout

// Give the last character an extent that wraps it to the next line in the repro case.
private val text =
  listOf(regularExtentChar, regularExtentChar, regularExtentChar, largerExtentChar)
    .joinToString(separator = "")

private val style =
  TextStyle(lineHeight = lineHeight, fontFamily = FontFamily(Font(fontResource)))

@SuppressLint("NewApi")
@Composable
@Preview
fun ComposeFontRepro() {
  StaticLayout.Builder.obtain("a", 0, 1, TextPaint(), 1024)
    .setUseBoundsForWidth(true)
    .build() // build method recycles the builder.

  lateinit var textLayout: TextLayoutResult
  var reTriggerLayoutStyleState by remember {
    mutableStateOf(style, policy = neverEqualPolicy())
  }
  Column(Modifier
    .fillMaxSize()
    .wrapContentSize()) {
    BasicText(
      text = text,
      style = reTriggerLayoutStyleState,
      onTextLayout = { textLayout = it },
    )
  }

  // force recomposition, necessary to reproduce the regression.
  // Doesn't actually change the style.
  LaunchedEffect(Unit) {
    reTriggerLayoutStyleState = style
  }
}
