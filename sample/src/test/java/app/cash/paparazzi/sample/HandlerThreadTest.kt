package app.cash.paparazzi.sample

import android.os.HandlerThread
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class HandlerThreadTest {
  @get:Rule
  val paparazzi = Paparazzi()

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
