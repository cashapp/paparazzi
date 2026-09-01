package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.processor.GenerateGreeting
import org.junit.Assert.assertEquals
import org.junit.Test

@GenerateGreeting
class KaptTest {
  @Test
  fun usesGeneratedCode() {
    assertEquals("generated-by-kapt", Greeting.text())
  }
}
