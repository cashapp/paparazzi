package app.cash.paparazzi

import android.view.ViewGroup

enum class ShrinkDirection(val layoutWidth: Int, val layoutHeight: Int) {
  VERTICAL(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
  HORIZONTAL(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
}
