package app.cash.paparazzi.api

import kotlin.reflect.KClass

@Target(
  AnnotationTarget.ANNOTATION_CLASS,
  AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class Paparazzi(
  // test
  val name: String = "default",
  val composableWrapper: KClass<out ComposableWrapper> = ComposableWrapper::class,

  // environment
  val envPlatformDir: String = "",
  val envAppTestDir: String = "",
  val envResDir: String = "",
  val envAssetsDir: String = "",
  val envPackageName: String = "",
  val envCompileSdkVersion: Int = -1,
  val envPlatformDataDir: String = "",
  val envResourcePackageNames: Array<String> = [],

  // device
  val deviceConfig: DeviceConfig = DeviceConfig.NEXUS_5,
  val deviceScreenHeight: Int = 1280,
  val deviceScreenWidth: Int = 768,
  val deviceXdpi: Int = 320,
  val deviceYdpi: Int = 320,
  val deviceScreenOrientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
  val deviceNightMode: NightMode = NightMode.NOTNIGHT,
  val deviceDensity: Density = Density.XHIGH,
  val deviceFontScale: Float = 1.0f,
  val deviceRatio: ScreenRatio = ScreenRatio.NOTLONG,
  val deviceSize: ScreenSize = ScreenSize.NORMAL,
  val deviceKeyboard: Keyboard = Keyboard.NOKEY,
  val deviceTouchScreen: TouchScreen = TouchScreen.FINGER,
  val deviceKeyboardState: KeyboardState = KeyboardState.SOFT,
  val deviceSoftButtons: Boolean = true,
  val deviceNavigation: Navigation = Navigation.NONAV,
  val deviceReleased: String = "November 13, 2012",

  // misc
  val theme: String = "android:Theme.Material.NoActionBar.Fullscreen",
  val renderingMode: RenderingMode = RenderingMode.NORMAL,
  val maxPercentDifference: Double = 0.1,
  val appCompatEnabled: Boolean = true
)
