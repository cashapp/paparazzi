package app.cash.paparazzi.plugin.test

import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class RecordTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun record() {
    val contents =
      paparazzi.context.assets.open("secret.txt").bufferedReader().use { it.readText() }
    val root = paparazzi.inflate<FrameLayout>(R.layout.root)
    val label = root.findViewById<TextView>(R.id.secret)
    label.text = contents
    paparazzi.snapshot(root)
  }
}
