package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.processor.GenerateGreeting
import org.junit.Assert.assertEquals
import org.junit.Test

@GenerateGreeting
class KspTest {
  @Test
  fun usesGeneratedCode() {
    assertEquals("generated-by-ksp", Greeting.TEXT)
  }
}
