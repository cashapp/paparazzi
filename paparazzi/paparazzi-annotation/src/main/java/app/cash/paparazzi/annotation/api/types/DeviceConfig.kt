package app.cash.paparazzi.annotation.api.types

import androidx.annotation.StringDef

object DeviceConfig {
  const val DEFAULT = ""

  const val NEXUS_4 = "NEXUS_4"
  const val NEXUS_5 = "NEXUS_5"
  const val NEXUS_7 = "NEXUS_7"
  const val NEXUS_10 = "NEXUS_10"
  const val NEXUS_5_LAND = "NEXUS_5_LAND"
  const val NEXUS_7_2012 = "NEXUS_7_2012"
  const val PIXEL_C = "PIXEL_C"
  const val PIXEL = "PIXEL"
  const val PIXEL_XL = "PIXEL_XL"
  const val PIXEL_2 = "PIXEL_2"
  const val PIXEL_2_XL = "PIXEL_2_XL"
  const val PIXEL_3 = "PIXEL_3"
  const val PIXEL_3_XL = "PIXEL_3_XL"
  const val PIXEL_3A = "PIXEL_3A"
  const val PIXEL_3A_XL = "PIXEL_3A_XL"
  const val PIXEL_4 = "PIXEL_4"
  const val PIXEL_4_XL = "PIXEL_4_XL"
  const val PIXEL_4A = "PIXEL_4A"
  const val PIXEL_5 = "PIXEL_5"
}

@Retention(AnnotationRetention.SOURCE)
@StringDef(
  value = [
    DeviceConfig.DEFAULT,
    DeviceConfig.NEXUS_4,
    DeviceConfig.NEXUS_5,
    DeviceConfig.NEXUS_7,
    DeviceConfig.NEXUS_10,
    DeviceConfig.NEXUS_5_LAND,
    DeviceConfig.NEXUS_7_2012,
    DeviceConfig.PIXEL_C,
    DeviceConfig.PIXEL,
    DeviceConfig.PIXEL_XL,
    DeviceConfig.PIXEL_2,
    DeviceConfig.PIXEL_2_XL,
    DeviceConfig.PIXEL_3,
    DeviceConfig.PIXEL_3_XL,
    DeviceConfig.PIXEL_3A,
    DeviceConfig.PIXEL_3A_XL,
    DeviceConfig.PIXEL_4,
    DeviceConfig.PIXEL_4_XL,
    DeviceConfig.PIXEL_4A,
    DeviceConfig.PIXEL_5,
  ]
)
annotation class Config
