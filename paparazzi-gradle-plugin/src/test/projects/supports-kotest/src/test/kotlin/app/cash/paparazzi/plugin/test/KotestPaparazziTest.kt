package app.cash.paparazzi.plugin.test

import android.view.Gravity
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.plugin.test.runner.PaparazziKotestListener
import io.kotest.core.spec.style.FunSpec

class KotestPaparazziTest : FunSpec({

  val listener = PaparazziKotestListener(Paparazzi())
  listeners(listener)

  test("verify paparazzi snapshot works with kotest listener") {
    val textView = listener.api.inflate<TextView>(android.R.layout.simple_list_item_1)
    textView.apply {
      text = "Kotest FunSpec Snapshot"
      textSize = 24f
      gravity = Gravity.CENTER
    }
    listener.api.snapshot(textView)
  }
})
