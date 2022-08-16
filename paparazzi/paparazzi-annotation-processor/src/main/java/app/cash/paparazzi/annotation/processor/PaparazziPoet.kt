package app.cash.paparazzi.annotation.processor

import app.cash.paparazzi.annotation.api.Paparazzi
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
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName

object PaparazziPoet {

  private val ruleClassName = ClassName("org.junit", "Rule")
  private val testAnnotationClassName = ClassName("org.junit", "Test")

  private val paparazziClassName = ClassName("app.cash.paparazzi", "Paparazzi")
  private val deviceConfigClassName = ClassName("app.cash.paparazzi", "DeviceConfig")
  private val detectEnvironmentName = MemberName("app.cash.paparazzi", "detectEnvironment")

  private val screenOrientationClassName = ClassName("com.android.resources", "ScreenOrientation")
  private val densityClassName = ClassName("com.android.resources", "Density")
  private val screenRatioClassName = ClassName("com.android.resources", "ScreenRatio")
  private val screenSizeClassName = ClassName("com.android.resources", "ScreenSize")
  private val keyboardClassName = ClassName("com.android.resources", "Keyboard")
  private val keyboardStateClassName = ClassName("com.android.resources", "KeyboardState")
  private val touchScreenClassName = ClassName("com.android.resources", "TouchScreen")
  private val navigationClassName = ClassName("com.android.resources", "Navigation")
  private val nightModeClassName = ClassName("com.android.resources", "NightMode")

  private val renderingModeClassName =
    ClassName("com.android.ide.common.rendering.api.SessionParams", "RenderingMode")


  fun buildFile(
    model: PaparazziModel,
    index: Int
  ): FileSpec {
    val className = model.functionName + "Test$index"

    return FileSpec.builder(model.packageName, "${Paparazzi::class.simpleName}_$className")
      .addType(
        TypeSpec.classBuilder(className)
          .addProperty(buildPaparazziProperty(model))
          .addFunction(buildTestFunction(model))
          .build()
      )
      .build()
  }

  private fun buildPaparazziProperty(model: PaparazziModel): PropertySpec {
    val ruleAnnotation = AnnotationSpec.builder(ruleClassName)
      .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
      .build()

    return PropertySpec.builder("paparazzi", paparazziClassName)
      .addAnnotation(ruleAnnotation)
      .initializer(model.buildInitializer())
      .build()
  }

  private fun buildTestFunction(model: PaparazziModel): FunSpec {
    val codeBuilder = CodeBlock.builder()

    if (model.previewParamProvider != null) {
      codeBuilder
        .addStatement(
          "%T().values.forEachIndexed { i, value ->", model.previewParamProvider.toTypeName()
        )
        .indent()
        .addStatement("paparazzi.snapshot(\"${model.previewParamTypeName}[\$i]\") {")
        .indent()
        .apply {
          if (model.composableWrapper != null) {
            addStatement("%T().wrap {", model.composableWrapper.toTypeName())
            indent()
          }
        }
        .addStatement("%L(value)", model.functionName)
        .apply {
          if (model.composableWrapper != null) {
            unindent()
            addStatement("}")
          }
        }
        .unindent()
        .addStatement("}")
        .unindent()
        .addStatement("}")
    } else {
      codeBuilder
        .addStatement("paparazzi.snapshot {")
        .indent()
        .apply {
          if (model.composableWrapper != null) {
            addStatement("%T().wrap {", model.composableWrapper.toTypeName())
            indent()
          }
        }
        .addStatement("%L()", model.functionName)
        .apply {
          if (model.composableWrapper != null) {
            unindent()
            addStatement("}")
          }
        }
        .unindent()
        .addStatement("}")
    }

    return FunSpec.builder(model.testName.ifEmpty { "default" }.lowercase())
      .addAnnotation(testAnnotationClassName)
      .addCode(codeBuilder.build())
      .build()
  }

  private fun PaparazziModel.buildInitializer(): CodeBlock {
    val overrides = CodeBlock.builder()
      .also {
        it.add(environment.buildOverride())
        it.add(device.buildOverride())
        if (theme.isNotEmpty()) {
          it.addStatement("theme = \"$theme\",")
        }
        if (renderingMode != RenderingMode.DEFAULT) {
          it.addStatement("renderingMode = %T.$renderingMode,", renderingModeClassName)
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
          it.addStatement("%T()", paparazziClassName)
        } else {
          it.addStatement("%T(", paparazziClassName)
          it.indent()
          it.add(overrides)
          it.unindent()
          it.addStatement(")")
        }
      }
      .build()
  }

  private fun EnvironmentModel.buildOverride(): CodeBlock {
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
          it.addStatement("environment = %M().copy(", detectEnvironmentName)
          it.indent()
          it.add(overrides)
          it.unindent()
          it.addStatement("),")
        }
      }
      .build()
  }

  private fun DeviceModel.buildOverride(): CodeBlock {
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
          it.addStatement("orientation = %T.$orientation,", screenOrientationClassName)
        }
        if (nightMode != NightMode.DEFAULT) {
          it.addStatement("nightMode = %T.$nightMode,", nightModeClassName)
        }
        if (density != Density.DEFAULT) {
          it.addStatement("density = %T.$density,", densityClassName)
        }
        if (fontScale > -1.0f) {
          it.addStatement("fontScale = ${fontScale}f,")
        }
        if (ratio != ScreenRatio.DEFAULT) {
          it.addStatement("ratio = %T.$ratio,", screenRatioClassName)
        }
        if (size != ScreenSize.DEFAULT) {
          it.addStatement("size = %T.$size,", screenSizeClassName)
        }
        if (keyboard != Keyboard.DEFAULT) {
          it.addStatement("keyboard = %T.$keyboard,", keyboardClassName)
        }
        if (touchScreen != TouchScreen.DEFAULT) {
          it.addStatement("touchScreen = %T.$touchScreen,", touchScreenClassName)
        }
        if (keyboardState != KeyboardState.DEFAULT) {
          it.addStatement("keyboardState = %T.$keyboardState,", keyboardStateClassName)
        }
        if (!softButtons) {
          it.addStatement("softButtons = false,")
        }
        if (navigation != Navigation.DEFAULT) {
          it.addStatement("navigation = %T.$navigation,", navigationClassName)
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
            it.addStatement("deviceConfig = %T.$config,", deviceConfigClassName)
          }
        } else {
          val baseConfig = if (config == DeviceConfig.DEFAULT) DeviceConfig.NEXUS_5 else config
          it.addStatement("deviceConfig = %T.$baseConfig.copy(", deviceConfigClassName)
          it.indent()
          it.add(overrides)
          it.unindent()
          it.addStatement("),")
        }
      }
      .build()
  }
}
