package app.cash.paparazzi.plugin.test

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.WindowManagerGlobal
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class ComposeOverlayShrinkTest {
  private var capturedImage: BufferedImage? = null
  private val capturedSizes = mutableListOf<Pair<Int, Int>>()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 300, screenHeight = 600, softButtons = false),
    renderingMode = RenderingMode.SHRINK,
    snapshotHandler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
        object : SnapshotHandler.FrameHandler {
          override fun handle(image: BufferedImage) {
            capturedImage = image
            capturedSizes += image.width to image.height
          }
          override fun close() = Unit
        }
      override fun close() = Unit
    }
  )

  @Test
  fun overlayWithPlaceholderHost() {
    paparazzi.snapshot {
      Spacer(Modifier.size(1.dp))
      FullSizeWindow()
    }
    val image = requireNotNull(capturedImage)
    assertEquals(300, image.width)
    assertEquals(600, image.height)
    assertEquals(0xFF888888.toInt(), image.getRGB(150, 10))
    assertEquals(java.awt.Color.BLUE.rgb, image.getRGB(150, 590))
  }

  @Test
  fun overlayAtAnotherDensity() {
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 300,
        screenHeight = 600,
        softButtons = false,
        density = com.android.resources.Density.MEDIUM
      )
    )
    overlayWithPlaceholderHost()
  }

  @Test
  fun overlayWithLargerHost() {
    paparazzi.snapshot {
      Spacer(Modifier.size(40.dp))
      FullSizeWindow()
    }
    assertEquals(listOf(300 to 600), capturedSizes)
  }

  @Test
  fun backingContentIsPreservedOutsideWindow() {
    paparazzi.snapshot {
      Box(Modifier.size(40.dp).background(Color.Red))
      FullSizeWindow(followHost = false)
    }
    val image = requireNotNull(capturedImage)
    assertEquals(listOf(300 to 600), capturedSizes)
    assertEquals(java.awt.Color.RED.rgb, image.getRGB(10, 10))
    assertEquals(java.awt.Color.BLUE.rgb, image.getRGB(150, 300))
  }

  @Test
  fun placeholderWithoutWindowKeepsItsSize() {
    paparazzi.snapshot { Spacer(Modifier.size(1.dp)) }
    assertEquals(listOf(3 to 3), capturedSizes)
  }

  @Test
  fun overlayAcrossFramesRestoresHostLayoutParams() {
    val host = overlayHost(paparazzi.context)
    paparazzi.gif(host, end = 100L, fps = 10)
    assertEquals(listOf(300 to 600, 300 to 600), capturedSizes)
    assertEquals(WRAP_CONTENT, host.layoutParams.width)
    assertEquals(WRAP_CONTENT, host.layoutParams.height)
    paparazzi.snapshot { Spacer(Modifier.size(1.dp)) }
    assertEquals(3 to 3, capturedSizes.last())
  }
}

// Public Android/Compose primitives: a separate application window follows its host's layout,
// while the main hierarchy contains only a placeholder. The scrim is drawn inside Compose.
@Composable
private fun FullSizeWindow(followHost: Boolean = true) {
  val host = LocalView.current
  val parentComposition = rememberCompositionContext()
  DisposableEffect(host) {
    val popup = ComposeView(host.context).apply {
      setViewTreeLifecycleOwner(host.findViewTreeLifecycleOwner())
      setViewTreeSavedStateRegistryOwner(host.findViewTreeSavedStateRegistryOwner())
      setParentCompositionContext(parentComposition)
      setContent {
        Box(Modifier.fillMaxSize().background(Color.Gray)) {
          Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp).background(Color.Blue))
        }
      }
    }
    val manager = host.context.getSystemService(WindowManager::class.java)
    val params = WindowManager.LayoutParams().apply {
      width = if (host.width == 0) MATCH_PARENT else host.width
      height = if (host.height == 0) WRAP_CONTENT else host.height
      if (!followHost) {
        width = 80
        height = 80
        gravity = android.view.Gravity.CENTER
      }
    }
    manager.addView(popup, params)
    val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
      params.width = right - left
      params.height = bottom - top
      manager.updateViewLayout(popup, params)
    }
    if (followHost) host.addOnLayoutChangeListener(listener)
    onDispose {
      host.removeOnLayoutChangeListener(listener)
      popup.disposeComposition()
      if (popup in WindowManagerGlobal.getInstance().windowViews) manager.removeViewImmediate(popup)
    }
  }
}

private fun overlayHost(context: android.content.Context) =
  ComposeView(context).apply {
    layoutParams = android.view.ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    setContent {
      Spacer(Modifier.size(1.dp))
      FullSizeWindow()
    }
  }
