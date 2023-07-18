package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AssetAccessTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun testViews() {
    val pairs = mapOf(
      "consumer/secret.txt" to "consumer",
      "producer1/secret.txt" to "producer1",
      "producer2/secret.txt" to "producer2",
      "external/secret.txt" to "external"
    )

    pairs.forEach { (key, value) ->
      val contents =
        paparazzi.context.assets.open(key).bufferedReader().use { it.readText() }
      assertThat(contents).isEqualTo(value)
    }
  }
}
