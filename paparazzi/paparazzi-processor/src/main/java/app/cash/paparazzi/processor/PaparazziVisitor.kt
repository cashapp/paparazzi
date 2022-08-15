package app.cash.paparazzi.processor

import app.cash.paparazzi.api.ComposableWrapper
import app.cash.paparazzi.api.Paparazzi
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSEmptyVisitor


class PaparazziVisitor(private val logger: KSPLogger) : KSEmptyVisitor<Unit, Sequence<PaparazziModel>>() {

    override fun defaultHandler(node: KSNode, data: Unit) = sequenceOf<PaparazziModel>()

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit): Sequence<PaparazziModel> {
        val annotations = function.annotations.findPaparazzi()
        val previewParam = function.previewParam()

        return annotations.map { annotation ->
            PaparazziModel(
                functionName = function.simpleName.asString(),
                packageName = function.packageName.asString(),
                testName = annotation.getArgument("name"),
                composableWrapper = annotation.composableWrapper(),
                environment = EnvironmentModel(
                    platformDir = annotation.getArgument("envPlatformDir"),
                    appTestDir = annotation.getArgument("envAppTestDir"),
                    resDir = annotation.getArgument("envResDir"),
                    assetsDir = annotation.getArgument("envAssetsDir"),
                    packageName = annotation.getArgument("envPackageName"),
                    compileSdkVersion = annotation.getArgument("envCompileSdkVersion"),
                    platformDataDir = annotation.getArgument("envPlatformDataDir"),
                    resourcePackageNames = annotation.getList("envResourcePackageNames"),
                ),
                device = DeviceModel(
                    config = annotation.getEnum("deviceConfig"),
                    screenHeight = annotation.getArgument("deviceScreenHeight"),
                    screenWidth = annotation.getArgument("deviceScreenWidth"),
                    xdpi = annotation.getArgument("deviceXdpi"),
                    ydpi = annotation.getArgument("deviceYdpi"),
                    orientation = annotation.getEnum("deviceScreenOrientation"),
                    nightMode = annotation.getEnum("deviceNightMode"),
                    density = annotation.getEnum("deviceDensity"),
                    fontScale = annotation.getArgument("deviceFontScale"),
                    ratio = annotation.getEnum("deviceRatio"),
                    size = annotation.getEnum("deviceSize"),
                    keyboard = annotation.getEnum("deviceKeyboard"),
                    touchScreen = annotation.getEnum("deviceTouchScreen"),
                    keyboardState = annotation.getEnum("deviceKeyboardState"),
                    softButtons = annotation.getArgument("deviceSoftButtons"),
                    navigation = annotation.getEnum("deviceNavigation"),
                    released = annotation.getArgument("deviceReleased"),
                ),

                theme = annotation.getArgument("theme"),
                renderingMode = annotation.getEnum("renderingMode"),
                appCompatEnabled = annotation.getArgument("appCompatEnabled"),
                maxPercentDifference = annotation.getArgument("maxPercentDifference"),

                previewParamTypeName = previewParam?.typeName(),
                previewParamProvider = previewParam?.previewParamProvider(),
            )
        }
    }

    private fun Sequence<KSAnnotation>.findPaparazzi(): Sequence<KSAnnotation> {
        val direct = filter { it.isPaparazzi() }
        val indirect = filterNot { it.isPaparazzi() || it.isSystem() }
            .map { it.parentAnnotations().findPaparazzi() }
            .flatten()
        return direct.plus(indirect)
    }

    private fun KSAnnotation.declaration() = annotationType.resolve().declaration

    private fun KSAnnotation.qualifiedName() = declaration().qualifiedName?.asString() ?: ""

    private fun KSAnnotation.isPaparazzi() = qualifiedName() == Paparazzi::class.qualifiedName.toString()

    private fun KSAnnotation.isSystem() = qualifiedName().let {
        it.startsWith("kotlin.") || it.startsWith("androidx.")
    }

    private fun KSAnnotation.parentAnnotations() = declaration().annotations

    private fun KSAnnotation.composableWrapper() = getArgument<KSType>("composableWrapper")
        .takeIf { it.declaration.qualifiedName?.asString() != ComposableWrapper::class.qualifiedName.toString() }

    private inline fun <reified T : Enum<T>> KSAnnotation.getEnum(name: String): T = getArgument<KSType>(name)
        .declaration.simpleName.asString()
        .let(::enumValueOf)

    private fun <T> KSAnnotation.getList(name: String) = getArgument<ArrayList<T>>(name).toList()

    private fun <T> KSAnnotation.getArgument(name: String) = arguments
        .first { it.name?.asString() == name }
        .let { it.value as T }

    private fun KSFunctionDeclaration.previewParam() = parameters.firstOrNull { param ->
        param.annotations.any { it.shortName.asString() == "PreviewParameter" }
    }

    private fun KSValueParameter.typeName() = type.resolve().declaration.simpleName.asString()

    private fun KSValueParameter.previewParamProvider() = annotations
        .firstOrNull { it.shortName.asString() == "PreviewParameter" }
        ?.arguments
        ?.firstOrNull { arg -> arg.name?.asString() == "provider" }
        ?.let { it.value as KSType }
}
