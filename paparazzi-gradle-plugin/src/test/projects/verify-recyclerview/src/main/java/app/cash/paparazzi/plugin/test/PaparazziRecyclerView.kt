package app.cash.paparazzi.plugin.test

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PaparazziRecyclerView(context: Context, attrs: AttributeSet) : RecyclerView(context, attrs) {
  class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)
  class Adapter : RecyclerView.Adapter<ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(TextView(parent.context))
    }

    override fun getItemCount(): Int = 5
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.view.text = "Paparazzi"
    }
  }
}
