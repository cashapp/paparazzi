package app.cash.paparazzi.accessibility

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.OVAL
import android.graphics.drawable.GradientDrawable.Orientation.TL_BR
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.internal.ImageUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class AccessibilityRenderExtensionTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val snapshotHandler = TestSnapshotVerifier()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    snapshotHandler = snapshotHandler,
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun `verify baseline`() {
    val view = buildView(paparazzi.context)
    paparazzi.snapshot(view, name = "accessibility")
  }

  @Test
  fun `test without layout params set`() {
    val view = buildView(paparazzi.context, null)
    paparazzi.snapshot(view, name = "without-layout-params")
  }

  @Test
  fun `verify changing view hierarchy order doesn't change accessibility colors`() {
    val view = buildView(paparazzi.context).apply {
      addView(View(context).apply { contentDescription = "Empty View" }, 0, LinearLayout.LayoutParams(0, 0))
    }
    paparazzi.snapshot(view, name = "accessibility-new-view")
  }

  private fun buildView(
    context: Context,
    rootLayoutParams: ViewGroup.LayoutParams? = ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
  ) =
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      rootLayoutParams?.let { layoutParams = it }
      addView(
        TextView(context).apply {
          id = 1
          text = "Text View Sample"
        }
      )

      addView(
        View(context).apply {
          id = 2
          layoutParams = LinearLayout.LayoutParams(100, 100)
          contentDescription = "Content Description Sample"
        }
      )

      addView(
        View(context).apply {
          id = 3
          layoutParams = LinearLayout.LayoutParams(100, 100).apply {
            setMarginsRelative(20, 20, 20, 20)
          }
          contentDescription = "Margin Sample"
        }
      )

      addView(
        View(context).apply {
          id = 4
          layoutParams = LinearLayout.LayoutParams(100, 100).apply {
            setMarginsRelative(20, 20, 20, 20)
          }
          foreground = GradientDrawable(TL_BR, intArrayOf(Color.YELLOW, Color.BLUE)).apply {
            shape = OVAL
          }
          contentDescription = "Foreground Drawable"
        }
      )

      addView(
        Button(context).apply {
          id = 5
          layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
          ).apply {
            gravity = Gravity.CENTER
          }
          text = "Button Sample"
        }
      )
    }

  private inner class TestSnapshotVerifier : SnapshotHandler {
    override fun newFrameHandler(
      snapshot: Snapshot,
      frameCount: Int,
      fps: Int
    ): SnapshotHandler.FrameHandler {
      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          val expected = File("src/test/resources/${snapshot.name}.png")
          ImageUtils.assertImageSimilar(
            relativePath = expected.path,
            image = image,
            goldenImage = ImageIO.read(expected),
            maxPercentDifferent = 0.1,
            failureDir = tempDir.newFolder()
          )
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
  }
}
