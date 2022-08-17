package app.cash.paparazzi.annotation.processor.poetry

import app.cash.paparazzi.annotation.api.config.Density
import app.cash.paparazzi.annotation.api.config.DeviceConfig
import app.cash.paparazzi.annotation.api.config.Keyboard
import app.cash.paparazzi.annotation.api.config.KeyboardState
import app.cash.paparazzi.annotation.api.config.Navigation
import app.cash.paparazzi.annotation.api.config.NightMode
import app.cash.paparazzi.annotation.api.config.RenderingMode
import app.cash.paparazzi.annotation.api.config.ScreenOrientation
import app.cash.paparazzi.annotation.api.config.ScreenRatio
import app.cash.paparazzi.annotation.api.config.ScreenSize
import app.cash.paparazzi.annotation.api.config.TouchScreen
import app.cash.paparazzi.annotation.processor.models.ComposableWrapperModel
import app.cash.paparazzi.annotation.processor.models.DeviceModel
import app.cash.paparazzi.annotation.processor.models.EnvironmentModel
import app.cash.paparazzi.annotation.processor.models.PaparazziModel
import app.cash.paparazzi.annotation.processor.models.PreviewParamModel
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

fun PreviewParamModel.buildConstructorProperty() =
  PropertySpec.builder("previewParam", type.toTypeName())
    .initializer("previewParam")
    .addModifiers(KModifier.PRIVATE)
    .addAnnotation(
      AnnotationSpec.builder(Imports.TestInject.testParameter)
        .addMember("valuesProvider = PreviewParamValuesProvider::class")
        .build()
    )
    .build()

fun PreviewParamModel.buildProviderClass(): TypeSpec {
  return TypeSpec.classBuilder("PreviewParamValuesProvider")
    .addSuperinterface(Imports.TestInject.testParameterValuesProvider)
    .addFunction(
      FunSpec.builder("provideValues")
        .addModifiers(OVERRIDE)
        .addStatement(
          "return %T().values.toList()",
          provider.toTypeName()
        )
        .build()
    )
    .build()
}

fun ComposableWrapperModel.buildConstructorProperty() =
  PropertySpec.builder("wrapperParam", value.toTypeName())
    .initializer("wrapperParam")
    .addModifiers(KModifier.PRIVATE)
    .addAnnotation(
      AnnotationSpec.builder(Imports.TestInject.testParameter)
        .addMember("valuesProvider = WrapperParamValuesProvider::class")
        .build()
    )
    .build()

fun ComposableWrapperModel.buildProviderClass(): TypeSpec {
  return TypeSpec.classBuilder("WrapperParamValuesProvider")
    .addSuperinterface(Imports.TestInject.testParameterValuesProvider)
    .addFunction(
      FunSpec.builder("provideValues")
        .addModifiers(OVERRIDE)
        .addStatement(
          "return %T().values.toList()",
          wrapper.toTypeName()
        )
        .build()
    )
    .build()
}

fun PaparazziModel.buildInitializer(): CodeBlock {
  val overrides = CodeBlock.builder()
    .also {
      it.add(environment.buildOverride())
      it.add(device.buildOverride())
      if (theme.isNotEmpty()) {
        it.addStatement("theme = \"$theme\",")
      }
      if (renderingMode != RenderingMode.DEFAULT) {
        it.addStatement("renderingMode = %T.$renderingMode,", Imports.Config.renderingMode)
      }
      if (!appCompatEnabled) {
        it.addStatement("appCompatEnabled = false,")
      }
      if (maxPercentDifference > -1.0) {
        it.addStatement("maxPercentDifference = $maxPercentDifference,")
      }
      // TODO snapshotHandler
      // TODO renderExtensions
    }
    .build()

  return CodeBlock.builder()
    .also {
      if (overrides.isEmpty()) {
        it.addStatement("%T()", Imports.Paparazzi.paparazzi)
      } else {
        it.addStatement("%T(", Imports.Paparazzi.paparazzi)
        it.indent()
        it.add(overrides)
        it.unindent()
        it.addStatement(")")
      }
    }
    .build()
}

fun EnvironmentModel.buildOverride(): CodeBlock {
  val overrides = CodeBlock.builder()
    .also {
      if (platformDir.isNotEmpty()) {
        it.addStatement("platformDir = \"$platformDir\",")
      }
      if (appTestDir.isNotEmpty()) {
        it.addStatement("appTestDir = \"$appTestDir\",")
      }
      if (resDir.isNotEmpty()) {
        it.addStatement("resDir = \"$resDir\",")
      }
      if (assetsDir.isNotEmpty()) {
        it.addStatement("assetsDir = \"$assetsDir\",")
      }
      if (packageName.isNotEmpty()) {
        it.addStatement("packageName = \"$packageName\",")
      }
      if (compileSdkVersion > -1) {
        it.addStatement("compileSdkVersion = $compileSdkVersion,")
      }
      if (platformDataDir.isNotEmpty()) {
        it.addStatement("platformDataDir = \"$platformDataDir\",")
      }
      if (resourcePackageNames.isNotEmpty()) {
        val stringList =
          resourcePackageNames.joinToString(prefix = "\"", separator = "\", \"", postfix = "\"")
        it.addStatement("resourcePackageNames = listOf($stringList),")
      }
    }
    .build()

  return CodeBlock.builder()
    .also {
      if (overrides.isNotEmpty()) {
        it.addStatement("environment = %M().copy(", Imports.Paparazzi.detectEnvironment)
        it.indent()
        it.add(overrides)
        it.unindent()
        it.addStatement("),")
      }
    }
    .build()
}

fun DeviceModel.buildOverride(): CodeBlock {
  val overrides = CodeBlock.builder()
    .also {
      if (screenHeight > -1) {
        it.addStatement("screenHeight = $screenHeight,")
      }
      if (screenWidth > -1) {
        it.addStatement("screenWidth = $screenWidth,")
      }
      if (xdpi > -1) {
        it.addStatement("xdpi = $xdpi,")
      }
      if (ydpi > -1) {
        it.addStatement("ydpi = $ydpi,")
      }
      if (orientation != ScreenOrientation.DEFAULT) {
        it.addStatement("orientation = %T.$orientation,", Imports.Config.screenOrientation)
      }
      if (nightMode != NightMode.DEFAULT) {
        it.addStatement("nightMode = %T.$nightMode,", Imports.Config.nightMode)
      }
      if (density != Density.DEFAULT) {
        it.addStatement("density = %T.$density,", Imports.Config.density)
      }
      if (fontScale > -1.0f) {
        it.addStatement("fontScale = ${fontScale}f,")
      }
      if (ratio != ScreenRatio.DEFAULT) {
        it.addStatement("ratio = %T.$ratio,", Imports.Config.screenRatio)
      }
      if (size != ScreenSize.DEFAULT) {
        it.addStatement("size = %T.$size,", Imports.Config.screenSize)
      }
      if (keyboard != Keyboard.DEFAULT) {
        it.addStatement("keyboard = %T.$keyboard,", Imports.Config.keyboard)
      }
      if (touchScreen != TouchScreen.DEFAULT) {
        it.addStatement("touchScreen = %T.$touchScreen,", Imports.Config.touchScreen)
      }
      if (keyboardState != KeyboardState.DEFAULT) {
        it.addStatement("keyboardState = %T.$keyboardState,", Imports.Config.keyboardState)
      }
      if (!softButtons) {
        it.addStatement("softButtons = false,")
      }
      if (navigation != Navigation.DEFAULT) {
        it.addStatement("navigation = %T.$navigation,", Imports.Config.navigation)
      }
      if (released.isNotEmpty()) {
        it.addStatement("released = \"$released\",")
      }
    }
    .build()

  return CodeBlock.builder()
    .also {
      if (overrides.isEmpty()) {
        if (config != DeviceConfig.DEFAULT) {
          it.addStatement("deviceConfig = %T.$config,", Imports.Paparazzi.deviceConfig)
        }
      } else {
        val baseConfig = if (config == DeviceConfig.DEFAULT) DeviceConfig.NEXUS_5 else config
        it.addStatement("deviceConfig = %T.$baseConfig.copy(", Imports.Paparazzi.deviceConfig)
        it.indent()
        it.add(overrides)
        it.unindent()
        it.addStatement("),")
      }
    }
    .build()
}
