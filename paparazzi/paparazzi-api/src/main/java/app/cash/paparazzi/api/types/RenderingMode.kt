package app.cash.paparazzi.api.types

import androidx.annotation.StringDef

object RenderingMode {
  const val DEFAULT = ""

  const val NORMAL = "NORMAL"
  const val V_SCROLL = "V_SCROLL"
  const val H_SCROLL = "H_SCROLL"
  const val FULL_EXPAND = "FULL_EXPAND"
  const val SHRINK = "SHRINK"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    RenderingMode.DEFAULT,
    RenderingMode.NORMAL,
    RenderingMode.V_SCROLL,
    RenderingMode.H_SCROLL,
    RenderingMode.FULL_EXPAND,
    RenderingMode.SHRINK,
  ]
)
annotation class SessionRenderingMode
