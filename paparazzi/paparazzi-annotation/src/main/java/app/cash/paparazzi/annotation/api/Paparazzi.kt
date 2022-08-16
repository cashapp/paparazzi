package app.cash.paparazzi.annotation.api

import app.cash.paparazzi.annotation.api.types.ComposableWrapper
import app.cash.paparazzi.annotation.api.types.Density
import app.cash.paparazzi.annotation.api.types.DeviceConfig
import app.cash.paparazzi.annotation.api.types.Keyboard
import app.cash.paparazzi.annotation.api.types.KeyboardState
import app.cash.paparazzi.annotation.api.types.Navigation
import app.cash.paparazzi.annotation.api.types.NightMode
import app.cash.paparazzi.annotation.api.types.RenderingMode
import app.cash.paparazzi.annotation.api.types.ScreenOrientation
import app.cash.paparazzi.annotation.api.types.ScreenRatio
import app.cash.paparazzi.annotation.api.types.ScreenSize
import app.cash.paparazzi.annotation.api.types.TouchScreen
import kotlin.reflect.KClass

@Target(
  AnnotationTarget.ANNOTATION_CLASS,
  AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class Paparazzi(
  // test
  val name: String = "",
  val composableWrapper: KClass<out ComposableWrapper> = ComposableWrapper::class,

  // environment
  val platformDir: String = "",
  val appTestDir: String = "",
  val resDir: String = "",
  val assetsDir: String = "",
  val packageName: String = "",
  val compileSdkVersion: Int = -1,
  val platformDataDir: String = "",
  val resourcePackageNames: Array<String> = [],

  // device
  val deviceConfig: DeviceConfig = DeviceConfig.DEFAULT,
  val screenHeight: Int = -1,
  val screenWidth: Int = -1,
  val xdpi: Int = -1,
  val ydpi: Int = -1,
  val orientation: ScreenOrientation = ScreenOrientation.DEFAULT,
  val nightMode: NightMode = NightMode.DEFAULT,
  val density: Density = Density.DEFAULT,
  val fontScale: Float = -1.0f,
  val ratio: ScreenRatio = ScreenRatio.DEFAULT,
  val size: ScreenSize = ScreenSize.DEFAULT,
  val keyboard: Keyboard = Keyboard.DEFAULT,
  val touchScreen: TouchScreen = TouchScreen.DEFAULT,
  val keyboardState: KeyboardState = KeyboardState.DEFAULT,
  val softButtons: Boolean = true,
  val navigation: Navigation = Navigation.DEFAULT,
  val released: String = "",

  // misc
  val theme: String = "",
  val renderingMode: RenderingMode = RenderingMode.DEFAULT,
  val maxPercentDifference: Double = -1.0,
  val appCompatEnabled: Boolean = true
)
