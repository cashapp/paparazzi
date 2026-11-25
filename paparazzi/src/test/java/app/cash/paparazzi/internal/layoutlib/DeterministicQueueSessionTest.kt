package app.cash.paparazzi.internal.layoutlib

import android.os.Handler
import android.os.Looper
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.PaparazziTestRule
import com.android.layoutlib.bridge.util.HandlerMessageQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.ref.Reference

class DeterministicQueueSessionTest {
  @get:Rule val fixture = PaparazziTestRule()

  @Test fun `prepared sessions already use the deterministic queue`() {
    assertTrue(currentQueue() is DeterministicHandlerMessageQueue)
  }

  @Test fun `every newly constructed session starts with an independent empty queue`() {
    val first = newSessionQueue()
    val second = newSessionQueue()
    assertTrue(first is DeterministicHandlerMessageQueue)
    assertTrue(second is DeterministicHandlerMessageQueue)
    assertNotSame(first, second)
    val handler = Handler(Looper.getMainLooper())
    first.add(handler, 0, Runnable {})
    assertEquals(1, first.size())
    assertEquals(0, second.size())
    Reference.reachabilityFence(handler)
  }

  @Test fun `the installed adapter dispatches equal time callbacks in posting order`() {
    val queue = newSessionQueue()
    val handlers = listOf(CollidingHandler(), CollidingHandler())
    val actual = mutableListOf<Int>()
    queue.add(handlers[0], 100, Runnable { actual += 1 })
    queue.add(handlers[1], 100, Runnable { actual += 2 })
    queue.add(handlers[0], 100, Runnable { actual += 3 })
    while (true) (queue.extractFirst(100) ?: break).run()
    assertEquals(listOf(1, 2, 3), actual)
    Reference.reachabilityFence(handlers)
  }

  @Test fun `configuration changes install a fresh deterministic queue too`() {
    val old = currentQueue()
    fixture.paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
    val fresh = currentQueue()
    assertTrue(fresh is DeterministicHandlerMessageQueue)
    assertNotSame(old, fresh)
  }

  private class CollidingHandler : Handler(Looper.getMainLooper()) {
    override fun hashCode(): Int = 0
  }

  private fun session(): Any {
    val context = fixture.paparazzi.context
    return context.javaClass.getMethod("getSessionInteractiveData").invoke(context)
  }

  private fun currentQueue(): HandlerMessageQueue {
    val session = session()
    return session.javaClass.getMethod("getHandlerMessageQueue").invoke(session) as HandlerMessageQueue
  }

  private fun newSessionQueue(): HandlerMessageQueue {
    val session = session().javaClass.getConstructor().newInstance()
    return session.javaClass.getMethod("getHandlerMessageQueue").invoke(session) as HandlerMessageQueue
  }
}
