package app.cash.paparazzi

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.internal.ImageUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class RenderExtensionTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  private val snapshotHandler = TestSnapshotVerifier()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    snapshotHandler = snapshotHandler,
    renderExtensions = setOf(
      WrappedRenderExtension(Color.DKGRAY),
      WrappedRenderExtension(Color.RED),
      WrappedRenderExtension(Color.GREEN),
      WrappedRenderExtension(Color.BLUE)
    )
  )

  @Test
  fun `call multiple snapshots on view`() {
    val view = buildView(paparazzi.context)
    paparazzi.snapshot(view, name = "wrapped")
    paparazzi.snapshot(view, name = "wrapped")
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
            failureDir = File("src/test/resources/${snapshot.name}").apply {
              mkdirs()
            }
          )
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
  }
}

class WrappedRenderExtension(val color: Int) : RenderExtension {
  override fun renderView(contentView: View): View =
    FrameLayout(contentView.context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setPadding(20, 20, 20, 20)

      setBackgroundColor(color)
      addView(contentView)
    }
}
