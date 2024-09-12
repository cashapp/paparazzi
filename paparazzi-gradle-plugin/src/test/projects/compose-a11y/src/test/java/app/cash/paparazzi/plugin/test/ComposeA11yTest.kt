package app.cash.paparazzi.plugin.test

import android.widget.LinearLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class ComposeA11yTest {
  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun `mixed compose usage`() {
    val mixedView = MixedView(paparazzi.context)
    paparazzi.snapshot(mixedView)
  }

  @Test
  @OptIn(ExperimentalMaterial3Api::class)
  fun modalBottomSheetMaterial3() {
    paparazzi.snapshot {
      ModalBottomSheet(
        onDismissRequest = {},
        sheetState = SheetState(
          skipPartiallyExpanded = true,
          density = LocalDensity.current,
          initialValue = SheetValue.Expanded
        )
      ) {
        Text(text = "Text 2")
      }
      Text(text = "Text 1")
    }
  }

  @Test
  fun `verify changing view hierarchy order doesn't change accessibility colors`() {
    val mixedView = MixedView(paparazzi.context).apply {
      addView(
        ComposeView(context).apply {
          id = 10
          setContent {
            Box(modifier = Modifier.size(50.dp)) {}
          }
        },
        0,
        LinearLayout.LayoutParams(0, 0)
      )
    }
    paparazzi.snapshot(mixedView)
  }

  @Test
  fun `verify clear and set semantics`() {
    paparazzi.snapshot {
      Box(modifier = Modifier.clickable {}) {
        Column(
          modifier = Modifier.clearAndSetSemantics {
            contentDescription = "OVERRIDDEN CONTENT DESCRIPTION"
          }
        ) {
          Text(text = "Text")
        }
      }
    }
  }
}
