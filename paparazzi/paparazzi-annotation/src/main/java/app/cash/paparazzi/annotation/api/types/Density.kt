package app.cash.paparazzi.annotation.api.types

import androidx.annotation.StringDef

object Density {
  const val DEFAULT = ""

  const val XXXHIGH = "XXXHIGH"
  const val DPI_560 = "DPI_560"
  const val XXHIGH = "XXHIGH"
  const val DPI_440 = "DPI_440"
  const val DPI_420 = "DPI_420"
  const val DPI_400 = "DPI_400"
  const val DPI_360 = "DPI_360"
  const val XHIGH = "XHIGH"
  const val DPI_260 = "DPI_260"
  const val DPI_280 = "DPI_280"
  const val DPI_300 = "DPI_300"
  const val DPI_340 = "DPI_340"
  const val HIGH = "HIGH"
  const val DPI_220 = "DPI_220"
  const val TV = "TV"
  const val DPI_200 = "DPI_200"
  const val DPI_180 = "DPI_180"
  const val MEDIUM = "MEDIUM"
  const val DPI_140 = "DPI_140"
  const val LOW = "LOW"
  const val ANYDPI = "ANYDPI"
  const val NODPI = "NODPI"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    Density.DEFAULT,
    Density.XXXHIGH,
    Density.DPI_560,
    Density.XXHIGH,
    Density.DPI_440,
    Density.DPI_420,
    Density.DPI_400,
    Density.DPI_360,
    Density.XHIGH,
    Density.DPI_260,
    Density.DPI_280,
    Density.DPI_300,
    Density.DPI_340,
    Density.HIGH,
    Density.DPI_220,
    Density.TV,
    Density.DPI_200,
    Density.DPI_180,
    Density.MEDIUM,
    Density.DPI_140,
    Density.LOW,
    Density.ANYDPI,
    Density.NODPI,
  ]
)
annotation class DeviceDensity
