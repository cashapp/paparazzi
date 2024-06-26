package app.cash.paparazzi.sample

import android.content.Context
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.LayoutlibRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.view.AttachInfo_Accessor
import android.view.ThreadedRenderer
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule

const val TEST_WIDTH = 90
const val TEST_HEIGHT = 90

class HardwareRendererTests {
  @get:Rule
  val paparazzi = Paparazzi()

  // @Test
  fun testBasicDrawCpuConsumer() {
    val reader = ImageReader.newInstance(
      TEST_WIDTH, TEST_HEIGHT, PixelFormat.RGBA_8888, 1,
      HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
    )
    assertNotNull(reader)
    val renderer = HardwareRenderer()
    var image: Image? = null

    try {
      val content = RenderNode("content")
      content.setPosition(0, 0, TEST_WIDTH, TEST_HEIGHT)
      val canvas = content.beginRecording()
      canvas.drawColor(Color.BLUE)
      content.endRecording()
      renderer.setContentRoot(content)

      renderer.setSurface(reader.surface)

      val syncResult = renderer.createRenderRequest()
        .setWaitForPresent(true)
        .syncAndDraw()

      assertEquals(HardwareRenderer.SYNC_OK, syncResult)

      image = reader.acquireNextImage()
      assertNotNull(image)
      val planes = image.planes
      assertNotNull(planes)
      assertEquals(1, planes.size)
      val plane = planes[0]
      assertEquals(4, plane.pixelStride)
      assertTrue((TEST_WIDTH * 4) <= plane.rowStride)

      val buffer = plane.buffer
      val red = buffer.get()
      val green = buffer.get()
      val blue = buffer.get()
      val alpha = buffer.get()
      println("$red $green $blue $alpha")
//      assertEquals(0, red)
//      assertEquals(0, green)
//      assertEquals(0xFF.toByte(), blue)
//      assertEquals(0xFF.toByte(), alpha)
    } finally {
      image?.close()
      renderer.destroy()
      reader.close()
    }
  }

//  @Test
  fun preIguana() {
//    val layoutlibRenderer = LayoutlibRenderer(paparazzi.context, true, "")
    val viewRoot = createTestView(paparazzi.context)
    paparazzi.snapshot(viewRoot)
    AttachInfo_Accessor.dispatchOnPreDraw(viewRoot)
//    viewRoot.updateDisplayListIfDirty()

    val viewRootImpl = AttachInfo_Accessor.getRootView(viewRoot)

    val threadedRenderer = ThreadedRenderer(paparazzi.context, true, "")
//    threadedRenderer.draw(view, viewRootImpl, object : ThreadedRenderer.DrawCallbacks {
//      override fun onPostDraw() {
//        println("onPostDraw")
//      }
//
//      override fun onPreDraw() {
//        println("onPreDraw")
//      }
//    }
  }

//  private fun ThreadedRenderer.draw(view: View, attachInfo: View.AttachInfo, callbacks: ThreadedRenderer.DrawCallbacks) {
//    try {
//      val drawMethod = ThreadedRenderer::class.java.getDeclaredMethod(
//        "draw",
//        View::class.java,
//        View.AttachInfo::class.java,
//        ThreadedRenderer.DrawCallbacks::class.java
//      )
//      drawMethod.isAccessible = true
//      drawMethod.invoke(this, view, attachInfo, callbacks)
//    } catch (e: Exception) {
//      throw RuntimeException(e)
//    }
//  }

  // reflective wrapper around package-private constructor
  private fun LayoutlibRenderer(
    context: Context,
    translucent: Boolean,
    name: String
  ): LayoutlibRenderer {
    try {
      val layoutlibRendererClass = Class.forName("android.view.LayoutlibRenderer")
      val constructor =
        layoutlibRendererClass.getDeclaredConstructor(
          Context::class.java,
          Boolean::class.java,
          String::class.java
        )
      constructor.isAccessible = true
      return constructor.newInstance(context, translucent, name) as LayoutlibRenderer
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  // reflective wrapper around package-private constructor
  private fun ThreadedRenderer(
    context: Context,
    translucent: Boolean,
    name: String
  ): ThreadedRenderer {
    try {
      val layoutlibRendererClass = Class.forName("android.view.ThreadedRenderer")
      val constructor =
        layoutlibRendererClass.getDeclaredConstructor(
          Context::class.java,
          Boolean::class.java,
          String::class.java
        )
      constructor.isAccessible = true
      return constructor.newInstance(context, translucent, name) as ThreadedRenderer
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  private fun createTestView(context: Context): FrameLayout {
    return FrameLayout(context).apply {
      setBackgroundColor(Color.valueOf(0xFF000033.toInt()).toArgb())
      addView(
        TextView(context).apply {
          text = "ExampleText"
          setTextColor(Color.WHITE)
        }
      )
    }
  }
}
