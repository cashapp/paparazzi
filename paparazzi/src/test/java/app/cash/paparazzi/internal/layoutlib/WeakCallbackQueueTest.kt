package app.cash.paparazzi.internal.layoutlib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch

class WeakCallbackQueueTest {
  @Test fun `equal timestamps follow global posting order rather than owner grouping`() {
    val queue = WeakCallbackQueue<Any>()
    val owners = listOf(CollidingOwner(), CollidingOwner())
    val actual = mutableListOf<Int>()
    queue.add(owners[0], 100, Runnable { actual += 1 })
    queue.add(owners[1], 100, Runnable { actual += 2 })
    queue.add(owners[0], 100, Runnable { actual += 3 })
    drain(queue, 100)
    assertEquals(listOf(1, 2, 3), actual)
    assertFalse(queue.isNotEmpty())
    Reference.reachabilityFence(owners)
  }

  @Test fun `earlier timestamps win over earlier posts and future callbacks remain queued`() {
    val queue = WeakCallbackQueue<Any>()
    val owners = listOf(Any(), Any())
    val actual = mutableListOf<Int>()
    queue.add(owners[0], 20, Runnable { actual += 2 })
    queue.add(owners[1], 5, Runnable { actual += 1 })
    assertNull(queue.extractFirst(4))
    assertEquals(2, queue.size())
    queue.extractFirst(5)!!.run()
    assertEquals(listOf(1), actual)
    assertNull(queue.extractFirst(19))
    assertTrue(queue.isNotEmpty())
    queue.extractFirst(20)!!.run()
    assertEquals(listOf(1, 2), actual)
    Reference.reachabilityFence(owners)
  }

  @Test fun `callbacks posted during draining join the tail for equal timestamps`() {
    val queue = WeakCallbackQueue<Any>()
    val owners = listOf(Any(), Any())
    val actual = mutableListOf<Int>()
    queue.add(
      owners[0], 10,
      Runnable {
        actual += 1
        queue.add(owners[1], 10, Runnable { actual += 3 })
      }
    )
    queue.add(owners[1], 10, Runnable { actual += 2 })
    drain(queue, 10)
    assertEquals(listOf(1, 2, 3), actual)
    Reference.reachabilityFence(owners)
  }

  @Test fun `posting the same Runnable twice keeps both callbacks`() {
    val queue = WeakCallbackQueue<Any>()
    val owner = Any()
    var calls = 0
    val callback = Runnable { calls++ }
    queue.add(owner, 0, callback)
    queue.add(owner, 0, callback)
    assertEquals(2, queue.size())
    drain(queue, 0)
    assertEquals(2, calls)
    Reference.reachabilityFence(owner)
  }

  @Test fun `negative initialization times and zero keep timestamp then FIFO ordering`() {
    val queue = WeakCallbackQueue<Any>()
    val owners = listOf(Any(), Any())
    val actual = mutableListOf<Int>()
    queue.add(owners[0], 0, Runnable { actual += 2 })
    queue.add(owners[1], -1, Runnable { actual += 1 })
    queue.add(owners[1], 0, Runnable { actual += 3 })
    drain(queue, 0)
    assertEquals(listOf(1, 2, 3), actual)
    Reference.reachabilityFence(owners)
  }

  @Test fun `clear removes callbacks and queue can be reused`() {
    val queue = WeakCallbackQueue<Any>()
    val owner = Any()
    var calls = 0
    queue.add(owner, 0, Runnable { calls++ })
    queue.clear()
    assertEquals(0, queue.size())
    assertFalse(queue.isNotEmpty())
    assertNull(queue.extractFirst(Long.MAX_VALUE))
    queue.add(owner, 0, Runnable { calls += 2 })
    drain(queue, 0)
    assertEquals(2, calls)
    Reference.reachabilityFence(owner)
  }

  @Test fun `callbacks do not retain an otherwise unreachable owner`() {
    val queue = WeakCallbackQueue<Any>()
    val reference = enqueueUnretainedOwner(queue)
    val deadline = System.nanoTime() + 5_000_000_000L
    while (reference.get() != null && System.nanoTime() < deadline) {
      System.gc()
      Thread.sleep(10)
    }
    assertNull("Owner should be weakly held", reference.get())
    assertEquals(0, queue.size())
    assertNull(queue.extractFirst(Long.MAX_VALUE))
  }

  @Test fun `concurrent posts retain every callback and per-owner ordering`() {
    val queue = WeakCallbackQueue<Any>()
    val owners = listOf(Any(), Any())
    val start = CountDownLatch(1)
    val actual = mutableListOf<Pair<Int, Int>>()
    val threads = owners.mapIndexed { ownerIndex, owner ->
      Thread {
        start.await()
        repeat(100) { index -> queue.add(owner, 42, Runnable { actual += ownerIndex to index }) }
      }.apply { this.start() }
    }
    start.countDown()
    threads.forEach {
      it.join(5000)
      assertFalse(it.isAlive)
    }
    assertEquals(200, queue.size())
    drain(queue, 42)
    assertEquals(200, actual.size)
    owners.indices.forEach { owner ->
      assertEquals((0 until 100).toList(), actual.filter { it.first == owner }.map { it.second })
    }
    Reference.reachabilityFence(owners)
  }

  // WeakHashMap traverses colliding owners independently of the callback posting sequence.
  private class CollidingOwner {
    override fun hashCode(): Int = 0
  }

  private fun enqueueUnretainedOwner(queue: WeakCallbackQueue<Any>): WeakReference<Any> {
    val owner = Any()
    queue.add(owner, 0, Runnable {})
    return WeakReference(owner)
  }

  private fun drain(queue: WeakCallbackQueue<Any>, now: Long) {
    while (true) (queue.extractFirst(now) ?: return).run()
  }
}
