package app.cash.paparazzi.plugin.test

import android.view.View.GONE
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
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
  fun dropDownMaterial3() {
    paparazzi.snapshot {
      Box(Modifier.fillMaxSize()) {
        DropdownMenu(
          expanded = true,
          onDismissRequest = { }
        ) {
          DropdownMenuItem(
            text = {
              Text(
                text = "Label 1"
              )
            },
            onClick = {}
          )
          DropdownMenuItem(
            text = {
              Text(
                text = "Label 2"
              )
            },
            onClick = {}
          )
          DropdownMenuItem(
            text = {
              Text(
                text = "Label 3"
              )
            },
            onClick = {}
          )
        }
      }
    }
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
      Text(modifier = Modifier.wrapContentSize(), text = "Text 1")
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

  @Test
  fun `verify hidden ComposeView content is not in legend`() {
    val view = ComposeView(paparazzi.context).apply {
      visibility = GONE
      setContent {
        Column {
          Text(text = "Text 1")
          Text(text = "Text 2")
        }
      }
    }

    paparazzi.snapshot(view)
  }

  @Test
  fun legendDoesNotScale() {
    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL.copy(fontScale = 2.0f))
    paparazzi.snapshot {
      Column(Modifier.background(Color.LightGray)) {
        androidx.compose.material.Text("Some text that will appear scaled in the UI, but not scaled in the legend")
      }
    }
  }

  @Test(expected = IllegalStateException::class)
  fun renderingModeSHRINKThrowsException() {
    paparazzi.unsafeUpdateConfig(renderingMode = RenderingMode.SHRINK)
    paparazzi.snapshot {
      Column(Modifier.background(Color.LightGray)) {
        Text("SHRINK and AccessibilityRenderExtension are not supported together")
      }
    }
  }
}
