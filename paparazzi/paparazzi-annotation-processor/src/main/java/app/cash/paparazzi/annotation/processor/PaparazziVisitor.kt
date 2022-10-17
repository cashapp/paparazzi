package app.cash.paparazzi.annotation.processor

import app.cash.paparazzi.annotation.api.config.ComposableWrapper
import app.cash.paparazzi.annotation.processor.models.ComposableWrapperModel
import app.cash.paparazzi.annotation.processor.models.DeviceModel
import app.cash.paparazzi.annotation.processor.models.EnvironmentModel
import app.cash.paparazzi.annotation.processor.models.PaparazziModel
import app.cash.paparazzi.annotation.processor.models.PreviewParamModel
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.visitor.KSEmptyVisitor

class PaparazziVisitor(
  private val annotations: Sequence<KSAnnotation>,
  private val logger: KSPLogger
  ) : KSEmptyVisitor<Unit, Sequence<PaparazziModel>>() {

  override fun defaultHandler(
    node: KSNode,
    data: Unit
  ) = sequenceOf<PaparazziModel>()

  override fun visitFunctionDeclaration(
    function: KSFunctionDeclaration,
    data: Unit
  ): Sequence<PaparazziModel> {
    val showClassIndex = annotations.count() > 1

    return annotations.map { annotation ->
      PaparazziModel(
        functionName = function.simpleName.asString(),
        packageName = function.packageName.asString(),
        showClassIndex = showClassIndex,
        testName = annotation.getArgument("name"),
        environment = EnvironmentModel(
          platformDir = annotation.getArgument("platformDir"),
          appTestDir = annotation.getArgument("appTestDir"),
          resDir = annotation.getArgument("resDir"),
          assetsDir = annotation.getArgument("assetsDir"),
          packageName = annotation.getArgument("packageName"),
          compileSdkVersion = annotation.getArgument("compileSdkVersion"),
          platformDataDir = annotation.getArgument("platformDataDir"),
          resourcePackageNames = annotation.getList("resourcePackageNames")
        ),
        device = DeviceModel(
          config = annotation.getEnum("deviceConfig"),
          screenHeight = annotation.getArgument("screenHeight"),
          screenWidth = annotation.getArgument("screenWidth"),
          xdpi = annotation.getArgument("xdpi"),
          ydpi = annotation.getArgument("ydpi"),
          orientation = annotation.getEnum("orientation"),
          nightMode = annotation.getEnum("nightMode"),
          density = annotation.getEnum("density"),
          fontScale = annotation.getArgument("fontScale"),
          fontScales = annotation.getList("fontScales"),
          ratio = annotation.getEnum("ratio"),
          size = annotation.getEnum("size"),
          keyboard = annotation.getEnum("keyboard"),
          touchScreen = annotation.getEnum("touchScreen"),
          keyboardState = annotation.getEnum("keyboardState"),
          softButtons = annotation.getArgument("softButtons"),
          navigation = annotation.getEnum("navigation"),
          released = annotation.getArgument("released")
        ),

        theme = annotation.getArgument("theme"),
        renderingMode = annotation.getEnum("renderingMode"),
        appCompatEnabled = annotation.getArgument("appCompatEnabled"),
        maxPercentDifference = annotation.getArgument("maxPercentDifference"),

        previewParam = function.previewParam()?.let {
          PreviewParamModel(
            type = it.type(),
            provider = it.previewParamProvider()
          )
        },
        composableWrapper = annotation.composableWrapper()?.let {
          val valueType = it.getSuperGenericType()
          logger.info("papa - ${it.declaration.qualifiedName?.asString()}")
          logger.info("papa - value type: ${valueType?.declaration?.qualifiedName?.asString()}")
          ComposableWrapperModel(
            wrapper = it,
            value = valueType
          )
        }
      )
    }
  }

  private fun KSAnnotation.composableWrapper() = getArgument<KSType>("composableWrapper")
    .takeIf { it.declaration.qualifiedName?.asString() != ComposableWrapper::class.qualifiedName.toString() }

  private fun <T> KSAnnotation.getList(name: String) = getArgument<ArrayList<T>>(name).toList()

  private inline fun <reified T : Enum<T>> KSAnnotation.getEnum(name: String): T =
    getArgument<KSType>(name)
      .declaration.simpleName.asString()
      .let(::enumValueOf)

  private fun <T> KSAnnotation.getArgument(name: String) = arguments
    .first { it.name?.asString() == name }
    .let { it.value as T }

  private fun KSFunctionDeclaration.previewParam() = parameters.firstOrNull { param ->
    param.annotations.any { it.shortName.asString() == "PreviewParameter" }
  }

  private fun KSValueParameter.type() = type.resolve()
  private fun KSValueParameter.typeName() = type().declaration.simpleName.asString()

  private fun KSValueParameter.previewParamProvider() = annotations
    .first { it.shortName.asString() == "PreviewParameter" }
    .arguments
    .first { arg -> arg.name?.asString() == "provider" }
    .let { it.value as KSType }

  private fun KSType.getSuperGenericType(): KSType? =
    (declaration as KSClassDeclaration).superTypes.first()
      .resolve().arguments.firstOrNull()?.type?.resolve()
}
