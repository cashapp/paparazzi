package app.cash.paparazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class AccessibilityBoundsRegressionTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 900, screenHeight = 1200),
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun boundsFollowMeasuredHeader() {
    paparazzi.snapshot {
      var headerHeight by remember { mutableIntStateOf(0) }
      var resultsOffset by remember { mutableIntStateOf(0) }
      // Apply the measured header position after composition. The layer moves during drawing,
      // after pre-draw accessibility collection, without another Android global layout.
      val measuredHeaderHeight = headerHeight
      SideEffect { resultsOffset = measuredHeaderHeight }
      MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Column(Modifier.onGloballyPositioned { headerHeight = it.size.height }) {
            Text("Partners", Modifier.padding(16.dp), style = MaterialTheme.typography.h6)
            Text(
              "Search partners",
              Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .background(Color.LightGray).padding(12.dp)
            )
          }
          Column(
            Modifier.fillMaxWidth().graphicsLayer { translationY = resultsOffset.toFloat() }
          ) {
            Text(
              "Search Results",
              Modifier.padding(16.dp).semantics { heading() },
              style = MaterialTheme.typography.h6
            )
            repeat(2) { index ->
              Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(16.dp)) {
                Text("Result ${index + 1}", style = MaterialTheme.typography.subtitle1)
                Text("Partner description", style = MaterialTheme.typography.body2)
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun boundsFollowLayoutDerivedPadding() {
    paparazzi.snapshot {
      var inputTop by remember { mutableIntStateOf(0) }
      MaterialTheme {
        // BoxWithConstraints subcomposes during measure, so the padding read from the input's
        // measured position settles in the measure and layout Compose runs from `dispatchDraw`,
        // after pre-draw accessibility collection.
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
          val inputTopInset = with(LocalDensity.current) {
            if (inputTop == 0) 0.dp else (constraints.maxHeight - inputTop).toDp()
          }
          Column(Modifier.align(Alignment.BottomStart).padding(bottom = inputTopInset + 12.dp)) {
            repeat(2) { index ->
              Text("Prompt ${index + 1}", Modifier.padding(16.dp))
            }
          }
          Text(
            "Message input",
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
              .onGloballyPositioned { inputTop = it.positionInRoot().y.toInt() }
              .background(Color.LightGray).padding(16.dp)
          )
        }
      }
    }
  }
}
