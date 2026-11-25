package app.cash.paparazzi.internal.layoutlib

import java.util.PriorityQueue
import java.util.WeakHashMap

/** Layoutlib callback scheduling with posting-order ties across weakly held owners. */
internal class WeakCallbackQueue<K : Any> {
  private class Callback(val dueTimeMillis: Long, val sequence: Long, val runnable: Runnable)

  private val pending = WeakHashMap<K, PriorityQueue<Callback>>()
  private var nextSequence = 0L

  @Synchronized
  fun add(owner: K, dueTimeMillis: Long, runnable: Runnable) {
    val callbacks = pending.getOrPut(owner) {
      PriorityQueue(compareBy<Callback> { it.dueTimeMillis }.thenBy { it.sequence })
    }
    callbacks.add(Callback(dueTimeMillis, nextSequence++, runnable))
  }

  @Synchronized
  fun extractFirst(nowMillis: Long): Runnable? {
    var selected: PriorityQueue<Callback>? = null
    var earliest: Callback? = null
    for (callbacks in pending.values) {
      val candidate = callbacks.peek() ?: continue
      if (candidate.dueTimeMillis > nowMillis) continue
      val previous = earliest
      if (previous == null || candidate.dueTimeMillis < previous.dueTimeMillis ||
        (candidate.dueTimeMillis == previous.dueTimeMillis && candidate.sequence < previous.sequence)
      ) {
        selected = callbacks
        earliest = candidate
      }
    }
    return selected?.remove()?.runnable
  }

  @Synchronized
  fun isNotEmpty(): Boolean = pending.values.any { it.isNotEmpty() }

  @Synchronized
  fun size(): Int = pending.values.sumOf { it.size }

  @Synchronized
  fun clear() {
    pending.clear()
    nextSequence = 0L
  }
}
