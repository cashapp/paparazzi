package app.cash.paparazzi.processor

import app.cash.paparazzi.api.Paparazzi
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
  private val paparazziClassName = ClassName("app.cash.paparazzi", "Paparazzi")
  private val deviceConfigClassName = ClassName("app.cash.paparazzi", "DeviceConfig")
  private val screenOrientationClassName = ClassName("app.cash.paparazzi", "ScreenOrientation")
  private val densityClassName = ClassName("app.cash.paparazzi", "Density")
  private val screenRatioClassName = ClassName("app.cash.paparazzi", "ScreenRatio")
  private val screenSizeClassName = ClassName("app.cash.paparazzi", "ScreenSize")
  private val keyboardClassName = ClassName("app.cash.paparazzi", "Keyboard")
  private val keyboardStateClassName = ClassName("app.cash.paparazzi", "KeyboardState")
  private val touchScreenClassName = ClassName("app.cash.paparazzi", "TouchScreen")
  private val navigationClassName = ClassName("app.cash.paparazzi", "Navigation")
  private val nightModeClassName = ClassName("app.cash.paparazzi", "NightMode")
  private val renderingModeClassName = ClassName("app.cash.paparazzi", "RenderingMode")

  private val detectEnvironmentName = MemberName("app.cash.paparazzi", "detectEnvironment")

  fun buildFile(model: PaparazziModel, index: Int): FileSpec {
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

    val codeBlock = CodeBlock.builder()
      .addStatement("%T(", paparazziClassName)
      .indent()
      .environment(model.environment)
      .device(model.device)
      .addStatement("theme = \"${model.theme}\",")
      .addStatement("renderingMode = %T.${model.renderingMode},", renderingModeClassName)
      .addStatement("appCompatEnabled = ${model.appCompatEnabled},")
      .addStatement("maxPercentDifference = ${model.maxPercentDifference},")
      .unindent()
      .addStatement(")")
      .build()

    return PropertySpec.builder("paparazzi", paparazziClassName)
      .addAnnotation(ruleAnnotation)
      .initializer(codeBlock)
      .build()
  }

  private fun buildTestFunction(model: PaparazziModel): FunSpec {
    val testAnnotationClassName = ClassName("org.junit", "Test")

    val codeBuilder = CodeBlock.builder()

    if (model.previewParamProvider != null) {
      codeBuilder
        .addStatement("%T().values.forEachIndexed { i, value ->", model.previewParamProvider.toTypeName())
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

    return FunSpec.builder(model.testName)
      .addAnnotation(testAnnotationClassName)
      .addCode(codeBuilder.build())
      .build()
  }

  private fun CodeBlock.Builder.environment(model: EnvironmentModel) = apply {
    addStatement("environment = %M().copy(", detectEnvironmentName)
    indent()

    if (model.platformDir.isNotEmpty()) {
      addStatement("platformDir = \"${model.platformDir}\",")
    }
    if (model.appTestDir.isNotEmpty()) {
      addStatement("appTestDir = \"${model.appTestDir}\",")
    }
    if (model.resDir.isNotEmpty()) {
      addStatement("resDir = \"${model.resDir}\",")
    }
    if (model.assetsDir.isNotEmpty()) {
      addStatement("assetsDir = \"${model.assetsDir}\",")
    }
    if (model.packageName.isNotEmpty()) {
      addStatement("packageName = \"${model.packageName}\",")
    }
    if (model.compileSdkVersion > -1) {
      addStatement("compileSdkVersion = ${model.compileSdkVersion},")
    }
    if (model.platformDataDir.isNotEmpty()) {
      addStatement("platformDataDir = \"${model.platformDataDir}\",")
    }
    if (model.resourcePackageNames.isNotEmpty()) {
      val stringList = model.resourcePackageNames.joinToString(prefix = "\"", separator = "\", \"", postfix = "\"")
      addStatement("resourcePackageNames = listOf($stringList),")
    }

    unindent()
    addStatement("),")
  }

  private fun CodeBlock.Builder.device(model: DeviceModel) = apply {
    addStatement("deviceConfig = %T.${model.config}.copy(", deviceConfigClassName)
    indent()
    addStatement("screenHeight = ${model.screenHeight},")
    addStatement("screenWidth = ${model.screenWidth},")
    addStatement("xdpi = ${model.xdpi},")
    addStatement("ydpi = ${model.ydpi},")
    addStatement("orientation = %T.${model.orientation},", screenOrientationClassName)
    addStatement("nightMode = %T.${model.nightMode},", nightModeClassName)
    addStatement("density = %T.${model.density},", densityClassName)
    addStatement("fontScale = ${model.fontScale}f,")
    addStatement("ratio = %T.${model.ratio},", screenRatioClassName)
    addStatement("size = %T.${model.size},", screenSizeClassName)
    addStatement("keyboard = %T.${model.keyboard},", keyboardClassName)
    addStatement("touchScreen = %T.${model.touchScreen},", touchScreenClassName)
    addStatement("keyboardState = %T.${model.keyboardState},", keyboardStateClassName)
    addStatement("softButtons = ${model.softButtons},")
    addStatement("navigation = %T.${model.navigation},", navigationClassName)
    addStatement("released = \"${model.released}\",")
    unindent()
    addStatement("),")
  }
}
