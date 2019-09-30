package app.cash.paparazzi.sample

import android.os.HandlerThread
import org.junit.Test

class HandlerThreadAndroidTest {
  @Test
  fun tryGetLooper() {
    val handlerThread = HandlerThread("testThread", 10)
    handlerThread.start()

    println("state: ${handlerThread.state}")
    println("isAlive: ${handlerThread.isAlive}")
    println("looper: ${handlerThread.looper}")
    println("state: ${handlerThread.state}")
    println("isAlive: ${handlerThread.isAlive}")
    println("looper: ${handlerThread.looper}")
    println("state: ${handlerThread.state}")
    println("isAlive: ${handlerThread.isAlive}")
  }
}