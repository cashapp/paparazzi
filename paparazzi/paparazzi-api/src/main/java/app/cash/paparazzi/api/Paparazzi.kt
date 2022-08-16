package app.cash.paparazzi.api

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import app.cash.paparazzi.api.types.Config
import app.cash.paparazzi.api.types.Density
import app.cash.paparazzi.api.types.DeviceConfig
import app.cash.paparazzi.api.types.DeviceDensity
import app.cash.paparazzi.api.types.DeviceKeyboard
import app.cash.paparazzi.api.types.DeviceKeyboardState
import app.cash.paparazzi.api.types.DeviceNavigation
import app.cash.paparazzi.api.types.DeviceNightMode
import app.cash.paparazzi.api.types.DeviceOrientation
import app.cash.paparazzi.api.types.DeviceScreenRatio
import app.cash.paparazzi.api.types.DeviceScreenSize
import app.cash.paparazzi.api.types.DeviceTouchScreen
import app.cash.paparazzi.api.types.Keyboard
import app.cash.paparazzi.api.types.KeyboardState
import app.cash.paparazzi.api.types.Navigation
import app.cash.paparazzi.api.types.NightMode
import app.cash.paparazzi.api.types.RenderingMode
import app.cash.paparazzi.api.types.ScreenOrientation
import app.cash.paparazzi.api.types.ScreenRatio
import app.cash.paparazzi.api.types.ScreenSize
import app.cash.paparazzi.api.types.SessionRenderingMode
import app.cash.paparazzi.api.types.TouchScreen
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
  @IntRange(from = 1) val compileSdkVersion: Int = -1,
  val platformDataDir: String = "",
  val resourcePackageNames: Array<String> = [],

  // device
  @Config val deviceConfig: String = DeviceConfig.DEFAULT,
  @IntRange(from = 1) val screenHeight: Int = -1,
  @IntRange(from = 1) val screenWidth: Int = -1,
  @IntRange(from = 1) val xdpi: Int = -1,
  @IntRange(from = 1) val ydpi: Int = -1,
  @DeviceOrientation val orientation: String = ScreenOrientation.DEFAULT,
  @DeviceNightMode val nightMode: String = NightMode.DEFAULT,
  @DeviceDensity val density: String = Density.DEFAULT,
  @FloatRange(from = 0.01) val fontScale: Float = -1.0f,
  @DeviceScreenRatio val ratio: String = ScreenRatio.DEFAULT,
  @DeviceScreenSize val size: String = ScreenSize.DEFAULT,
  @DeviceKeyboard val keyboard: String = Keyboard.DEFAULT,
  @DeviceTouchScreen val touchScreen: String = TouchScreen.DEFAULT,
  @DeviceKeyboardState val keyboardState: String = KeyboardState.DEFAULT,
  val softButtons: Boolean = true,
  @DeviceNavigation val navigation: String = Navigation.DEFAULT,
  val released: String = "",

  // misc
  val theme: String = "",
  @SessionRenderingMode val renderingMode: String = RenderingMode.DEFAULT,
  @FloatRange(from = 0.0) val maxPercentDifference: Double = -1.0,
  val appCompatEnabled: Boolean = true
)
