package app.cash.paparazzi.sample

import androidx.compose.material.Text
import androidx.compose.runtime.SideEffect
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class LazyFrameHandlerComposeTest {
  private var frames = 0
  private var closes = 0
  private val handler = object : SnapshotHandler {
    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler =
      object : SnapshotHandler.LazyFrameHandler {
        override fun handle(image: () -> BufferedImage) {
          frames++
        }
        override fun close() {
          closes++
        }
      }
    override fun close() = Unit
  }

  @get:Rule
  val paparazzi = Paparazzi(snapshotHandler = handler)

  @Test
  fun discardedSnapshotsAndAnimationsStillRunComposition() {
    var compositions = 0
    paparazzi.snapshot {
      SideEffect { compositions++ }
      Text("Discarded still image")
    }
    paparazzi.gif(start = 0, end = 500, fps = 4) {
      SideEffect { compositions++ }
      Text("Discarded animation")
    }
    assertEquals(2, compositions)
    assertEquals(4, frames)
    assertEquals(2, closes)
  }
}
