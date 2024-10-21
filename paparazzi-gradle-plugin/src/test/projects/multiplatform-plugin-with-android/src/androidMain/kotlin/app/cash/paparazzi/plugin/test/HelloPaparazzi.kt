package app.cash.paparazzi.plugin.test

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView

@SuppressLint("SetTextI18n")
class HelloPaparazzi(context: Context) : TextView(context) {
  init {
    text = "Hello Paparazzi!"
  }
}
