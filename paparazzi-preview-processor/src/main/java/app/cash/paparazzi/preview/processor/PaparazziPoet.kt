package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.buildCodeBlock

internal object PaparazziPoet {
  fun buildFiles(functions: Sequence<KSFunctionDeclaration>, isTest: Boolean, env: EnvironmentOptions) =
    if (isTest) {
      emptyList()
    } else {
      listOf(
        buildAnnotationsFile(
          fileName = "PaparazziPreviews",
          propertyName = "paparazziPreviews",
          functions = functions,
          env = env
        )
      )
    }

  @Suppress("SameParameterValue")
  private fun buildAnnotationsFile(
    fileName: String,
    propertyName: String,
    functions: Sequence<KSFunctionDeclaration>,
    env: EnvironmentOptions
  ) = FileSpec.scriptBuilder(fileName, env.namespace)
    .addCode(
      buildCodeBlock {
        addStatement(
          "internal val %L = listOf<%L.PaparazziPreviewData>(", propertyName, PACKAGE_NAME
        )
        indent()

        if (functions.count() == 0) {
          addEmpty()
        } else {
          functions.process { func, preview, previewParam ->
            val visibilityCheck = checkVisibility(func, previewParam)
            val snapshotName = func.snapshotName(env)

            when {
              visibilityCheck.isPrivate -> addError(
                visibilityCheck = visibilityCheck,
                function = func,
                snapshotName = snapshotName,
                preview = preview,
                previewParam = previewParam
              )

              previewParam != null -> addProvider(
                function = func,
                snapshotName = snapshotName,
                preview = preview,
                previewParam = previewParam
              )

              else -> addDefault(
                function = func,
                snapshotName = snapshotName,
                preview = preview
              )
            }
          }
        }

        unindent()
        addStatement(")")
      }
    )
    .build()

  private fun CodeBlock.Builder.addEmpty() {
    addStatement("%L.PaparazziPreviewData.Empty,", PACKAGE_NAME)
  }

  private fun Sequence<KSFunctionDeclaration>.process(
    block: (KSFunctionDeclaration, PreviewModel, KSValueParameter?) -> Unit
  ) = flatMap { func ->
    val previewParam = func.parameters.firstOrNull { param ->
      param.annotations.any { it.isPreviewParameter() }
    }
    func.findDistinctPreviews()
      .map { AcceptableAnnotationsProcessData(func, it, previewParam) }
  }.forEach { (func, preview, previewParam) ->
    block(func, preview, previewParam)
  }

  private data class AcceptableAnnotationsProcessData(
    val func: KSFunctionDeclaration,
    val model: PreviewModel,
    val previewParam: KSValueParameter?
  )

  private fun CodeBlock.Builder.addError(
    visibilityCheck: VisibilityCheck,
    function: KSFunctionDeclaration,
    snapshotName: String,
    preview: PreviewModel,
    previewParam: KSValueParameter?
  ) {
    val qualifiedName = if (visibilityCheck.isFunctionPrivate) {
      function.qualifiedName?.asString()
    } else {
      previewParam?.previewParamProvider()?.qualifiedName?.asString()
    }

    addStatement("%L.PaparazziPreviewData.Error(", PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement(
      "message = %S,",
      "$qualifiedName is private. Make it internal or public to generate a snapshot."
    )
    addPreviewData(preview)
    unindent()
    addStatement("),")
  }

  private fun CodeBlock.Builder.addProvider(
    function: KSFunctionDeclaration,
    snapshotName: String,
    preview: PreviewModel,
    previewParam: KSValueParameter
  ) {
    addStatement("%L.PaparazziPreviewData.Provider(", PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement("composable = { %L(it) },", function.qualifiedName?.asString())
    addPreviewParameterData(previewParam)
    addPreviewData(preview)
    unindent()
    addStatement("),")
  }

  private fun CodeBlock.Builder.addDefault(
    function: KSFunctionDeclaration,
    snapshotName: String,
    preview: PreviewModel
  ) {
    addStatement("%L.PaparazziPreviewData.Default(", PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement("composable = { %L() },", function.qualifiedName?.asString())
    addPreviewData(preview)
    unindent()
    addStatement("),")
  }

  private fun CodeBlock.Builder.addPreviewData(preview: PreviewModel) {
    addStatement("preview = %L.PreviewData(", PACKAGE_NAME)
    indent()

    preview.fontScale.takeIf { it != 1f }
      ?.let { addStatement("fontScale = %Lf,", it) }

    preview.device.takeIf { it.isNotEmpty() }
      ?.let { addStatement("device = %S,", it) }

    preview.widthDp.takeIf { it > -1 }
      ?.let { addStatement("widthDp = %L,", it) }

    preview.heightDp.takeIf { it > -1 }
      ?.let { addStatement("heightDp = %L,", it) }

    preview.uiMode.takeIf { it != 0 }
      ?.let { addStatement("uiMode = %L,", it) }

    preview.locale.takeIf { it.isNotEmpty() }
      ?.let { addStatement("locale = %S,", it) }

    preview.backgroundColor.takeIf { it != 0L && preview.showBackground }
      ?.let { addStatement("backgroundColor = %S", it.toString(16)) }

    unindent()
    addStatement("),")
  }

  private fun CodeBlock.Builder.addPreviewParameterData(previewParam: KSValueParameter) {
    addStatement("previewParameter = %L.PreviewParameterData(", PACKAGE_NAME)
    indent()
    addStatement("name = %S,", previewParam.name?.asString())
    val previewParamProvider = previewParam.previewParamProvider()
    val isClassObject = previewParamProvider.closestClassDeclaration()?.classKind == ClassKind.OBJECT
    val previewParamProviderInstantiation =
      "${previewParamProvider.qualifiedName?.asString()}${if (isClassObject) "" else "()"}"
    addStatement("values = %L.values,", previewParamProviderInstantiation)
    unindent()
    addStatement("),")
  }

  private fun KSFunctionDeclaration.snapshotName(env: EnvironmentOptions) =
    buildList {
      containingFile
        ?.let { "${it.packageName.asString()}.${it.fileName.removeSuffix(".kt")}" }
        ?.removePrefix("${env.namespace}.")
        ?.replace(".", "_")
        ?.let { add(it) }
      add(simpleName.asString())
    }.joinToString("_")

  private fun checkVisibility(function: KSFunctionDeclaration, previewParam: KSValueParameter?) =
    VisibilityCheck(
      isFunctionPrivate = function.getVisibility() == Visibility.PRIVATE,
      isPreviewParamProviderPrivate = previewParam?.previewParamProvider()
        ?.getVisibility() == Visibility.PRIVATE
    )
}

internal data class VisibilityCheck(
  val isFunctionPrivate: Boolean,
  val isPreviewParamProviderPrivate: Boolean
) {
  val isPrivate = isFunctionPrivate
}
