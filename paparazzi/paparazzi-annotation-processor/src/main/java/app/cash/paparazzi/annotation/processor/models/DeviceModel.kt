package app.cash.paparazzi.annotation.processor.models

import app.cash.paparazzi.annotation.api.config.Density
import app.cash.paparazzi.annotation.api.config.DeviceConfig
import app.cash.paparazzi.annotation.api.config.Keyboard
import app.cash.paparazzi.annotation.api.config.KeyboardState
import app.cash.paparazzi.annotation.api.config.Navigation
import app.cash.paparazzi.annotation.api.config.NightMode
import app.cash.paparazzi.annotation.api.config.ScreenOrientation
import app.cash.paparazzi.annotation.api.config.ScreenRatio
import app.cash.paparazzi.annotation.api.config.ScreenSize
import app.cash.paparazzi.annotation.api.config.TouchScreen

data class DeviceModel(
  val config: DeviceConfig,
  val screenHeight: Int,
  val screenWidth: Int,
  val xdpi: Int,
  val ydpi: Int,
  val orientation: ScreenOrientation,
  val nightMode: NightMode,
  val density: Density,
  val fontScale: Float,
  val fontScales: List<Float>,
  val ratio: ScreenRatio,
  val size: ScreenSize,
  val keyboard: Keyboard,
  val touchScreen: TouchScreen,
  val keyboardState: KeyboardState,
  val softButtons: Boolean,
  val navigation: Navigation,
  val released: String
)
