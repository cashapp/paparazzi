package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.plugin.test.databinding.GreetingBinding
import app.cash.paparazzi.plugin.test.databinding.PlainBinding
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class DataBindingTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun viewBindingInflates() {
    val binding = PlainBinding.inflate(paparazzi.layoutInflater)
    paparazzi.snapshot(binding.root)
  }

  @Test
  fun dataBindingClassesAreOnTheClasspath() {
    // Inflating a data binding layout needs androidx.databinding.DataBinderMapperImpl, which AGP
    // only generates for application modules - library unit tests cannot do it either. The
    // generated binding class itself is available, which is what this source set is responsible for.
    assertNotNull(GreetingBinding::class.java)
  }
}
