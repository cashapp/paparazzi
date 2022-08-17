package app.cash.paparazzi.annotation.processor.poetry

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object Imports {

  object JUnit {
    val rule = ClassName("org.junit", "Rule")
    val test = ClassName("org.junit", "Test")
    val runWith = ClassName("org.junit.runner", "RunWith")
  }

  object TestInject {
    val testParameterInjector =
      ClassName("com.google.testing.junit.testparameterinjector", "TestParameterInjector")
    val testParameter =
      ClassName("com.google.testing.junit.testparameterinjector", "TestParameter")
    val testParameterValuesProvider =
      ClassName(
        "com.google.testing.junit.testparameterinjector.TestParameter",
        "TestParameterValuesProvider"
      )
  }

  object Paparazzi {
    val paparazzi = ClassName("app.cash.paparazzi", "Paparazzi")
    val deviceConfig = ClassName("app.cash.paparazzi", "DeviceConfig")
    val detectEnvironment = MemberName("app.cash.paparazzi", "detectEnvironment")
  }

  object Config {
    val screenOrientation = ClassName("com.android.resources", "ScreenOrientation")
    val density = ClassName("com.android.resources", "Density")
    val screenRatio = ClassName("com.android.resources", "ScreenRatio")
    val screenSize = ClassName("com.android.resources", "ScreenSize")
    val keyboard = ClassName("com.android.resources", "Keyboard")
    val keyboardState = ClassName("com.android.resources", "KeyboardState")
    val touchScreen = ClassName("com.android.resources", "TouchScreen")
    val navigation = ClassName("com.android.resources", "Navigation")
    val nightMode = ClassName("com.android.resources", "NightMode")
    val renderingMode =
      ClassName("com.android.ide.common.rendering.api.SessionParams", "RenderingMode")
  }
}
