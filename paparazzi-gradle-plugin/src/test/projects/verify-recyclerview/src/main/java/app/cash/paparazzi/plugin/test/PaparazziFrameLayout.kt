package app.cash.paparazzi.plugin.test

import android.content.Context
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import app.cash.paparazzi.plugin.test.databinding.InnerViewInflateBinding

class PaparazziFrameLayout(context: Context) : FrameLayout(context) {
  init {
    inflate(context, R.layout.inner_view_inflate, this)
    val binding = InnerViewInflateBinding.bind(this)
    val recyclerView = binding.list
    recyclerView.adapter = PaparazziRecyclerView.Adapter()
    recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
  }
}
