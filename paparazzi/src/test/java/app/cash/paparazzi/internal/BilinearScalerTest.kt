/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** How [BilinearScaler] splits a pass across threads, including failures. */
class BilinearScalerTest {
  private val srcWidth = 1080
  private val srcHeight = 2340
  private val dstWidth = 922
  private val dstHeight = 2000
  private val src = IntArray(srcWidth * srcHeight).also { pixels ->
    val random = Random(7)
    for (i in pixels.indices) pixels[i] = random.nextInt()
  }
  private val serial = scale(bands = 1)

  @Test
  fun bandsMatchSerialOutput() {
    for (bands in 2..4) {
      assertThat(scale(bands = bands)).isEqualTo(serial)
      assertThat(scale(bands = bands, executor = { Thread(it).start() })).isEqualTo(serial)
    }
  }

  @Test
  fun rejectedBandsRunOnCaller() {
    var rejected = 0
    val result = scale(bands = 4, executor = {
      rejected++
      throw RejectedExecutionException()
    })
    assertThat(result).isEqualTo(serial)
    assertThat(rejected).isEqualTo(3)
  }

  @Test
  fun busyHelpersDoNotQueueWork() {
    // An executor configured like the shared helpers, with every helper occupied: a scale must then
    // neither queue work nor wait for a helper.
    val executor = BilinearScaler.newHelperExecutor()
    val release = CountDownLatch(1)
    val started = CountDownLatch(3)
    try {
      repeat(3) {
        executor.execute {
          started.countDown()
          release.await()
        }
      }
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
      assertThrows(RejectedExecutionException::class.java) { executor.execute {} }
      assertThat(scale(bands = 4, executor = executor)).isEqualTo(serial)
      assertThat(executor.queue).isEmpty()
      assertThat(executor.completedTaskCount).isEqualTo(0)
    } finally {
      release.countDown()
      executor.shutdown()
    }
  }

  @Test
  fun sharedHelpersAreConfiguredLikeNewHelperExecutor() {
    val helpers = BilinearScaler.helpers
    assertThat(helpers.corePoolSize).isEqualTo(0)
    assertThat(helpers.maximumPoolSize).isEqualTo(3)
    assertThat(helpers.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(1)
    assertThat(helpers.queue).isInstanceOf(SynchronousQueue::class.java)
  }

  @Test
  fun drainNeverSelfSuppressesRepeatedException() {
    val shared = IllegalStateException("shared")
    val tasks = List(3) { CompletableFuture.failedFuture<Unit>(shared) }
    val fromTasks = BilinearScaler.drain(tasks, failure = null)
    assertThat(fromTasks).isSameInstanceAs(shared)
    assertThat(shared.suppressed).isEmpty()
    assertThat(BilinearScaler.drain(tasks, failure = shared)).isSameInstanceAs(shared)
    assertThat(shared.suppressed).isEmpty()

    val primary = IllegalStateException("primary")
    val other = IllegalStateException("other")
    val mixed = listOf(CompletableFuture.failedFuture<Unit>(other), CompletableFuture.failedFuture(other))
    assertThat(BilinearScaler.drain(mixed, primary)).isSameInstanceAs(primary)
    assertThat(primary.suppressed.toList()).containsExactly(other)
  }

  @Test
  fun submissionFailureDrainsSubmittedBands() {
    val submitted = CopyOnWriteArrayList<Future<*>>()
    val executor = Executor { task ->
      if (submitted.size == 1) throw IllegalStateException("submit failed")
      submitted += task as Future<*>
      slowly(task)
    }
    val error = assertThrows(IllegalStateException::class.java) { scale(bands = 4, executor = executor) }
    assertThat(error).hasMessageThat().isEqualTo("submit failed")
    assertThat(submitted).hasSize(1)
    assertThat(submitted.single().isDone).isTrue()
  }

  @Test
  fun bandFailuresAreReportedAfterAllBandsFinish() {
    val submitted = CopyOnWriteArrayList<Future<*>>()
    val executor = Executor { task ->
      submitted += task as Future<*>
      slowly(task)
    }
    // Too few source pixels: every band fails with an out-of-bounds read.
    val error = assertThrows(ArrayIndexOutOfBoundsException::class.java) {
      BilinearScaler.scale(IntArray(10), srcWidth, srcHeight, dstWidth, dstHeight, bands = 4, executor = executor)
    }
    assertThat(submitted).hasSize(3)
    assertThat(submitted.all { it.isDone }).isTrue()
    assertThat(error.suppressed).hasLength(3)
  }

  @Test
  fun interruptedCallerStillDrainsAndKeepsInterruptStatus() {
    val submitted = CopyOnWriteArrayList<Future<*>>()
    val executor = Executor { task ->
      submitted += task as Future<*>
      slowly(task)
    }
    Thread.currentThread().interrupt()
    try {
      assertThat(scale(bands = 4, executor = executor)).isEqualTo(serial)
      assertThat(submitted.all { it.isDone }).isTrue()
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun helperThreadsCarryNoCallerContext() {
    val local = InheritableThreadLocal<String>().apply { set("test state") }
    val seen = CopyOnWriteArrayList<Any>()
    val thread = BilinearScaler.newHelperThread {
      seen += local.get() ?: "no inherited value"
      seen += Thread.currentThread().contextClassLoader ?: "no context class loader"
    }
    thread.start()
    thread.join()
    assertThat(thread.isDaemon).isTrue()
    assertThat(seen).containsExactly("no inherited value", "no context class loader").inOrder()
  }

  /** Runs [task] on a new thread after a delay, so the caller must wait for it. */
  private fun slowly(task: Runnable) {
    Thread {
      Thread.sleep(200)
      task.run()
    }.start()
  }

  private fun scale(bands: Int, executor: Executor? = null): IntArray =
    if (executor == null) {
      BilinearScaler.scale(src, srcWidth, srcHeight, dstWidth, dstHeight, bands = bands)
    } else {
      BilinearScaler.scale(src, srcWidth, srcHeight, dstWidth, dstHeight, bands = bands, executor = executor)
    }
}
