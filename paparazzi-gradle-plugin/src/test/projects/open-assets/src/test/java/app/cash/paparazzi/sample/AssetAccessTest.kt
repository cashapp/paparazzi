package app.cash.paparazzi.sample

import app.cash.paparazzi.Paparazzi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class AssetAccessTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun testViews() {
    val contents =
      paparazzi.context.assets.open("secret.txt").bufferedReader().use { it.readText() }
    assertThat(contents).isEqualTo("sup")
  }
}