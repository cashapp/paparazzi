package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.buildCodeBlock

internal object PaparazziPoet {
  fun buildFiles(
    functions: Sequence<KSFunctionDeclaration>,
    isTest: Boolean,
    env: EnvironmentOptions
  ) =
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
  ) =
    FileSpec.scriptBuilder(fileName, env.namespace)
      .addCode(
        buildCodeBlock {
          addStatement("internal val %L = listOf<%L.PaparazziPreviewData>(", propertyName, PACKAGE_NAME)
          indent()

          if (functions.count() == 0) {
            addEmpty()
          } else {
            functions.process { func, previewParam ->
              val visibilityCheck = checkVisibility(func)
              val snapshotName = func.snapshotName(env)

              when {
                visibilityCheck.isPrivate -> addError(
                  function = func,
                  snapshotName = snapshotName,
                  buildErrorMessage = {
                    "$it is private. Make it internal or public to generate a snapshot."
                  }
                )
                previewParam != null -> addError(
                  function = func,
                  snapshotName = snapshotName,
                  buildErrorMessage = {
                    "$it preview uses PreviewParameters which aren't currently supported."
                  }
                )
                else -> addDefault(
                  function = func,
                  snapshotName = snapshotName
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
    block: (KSFunctionDeclaration, KSValueParameter?) -> Unit
  ) =
    flatMap { func ->
      val previewParam = func.parameters.firstOrNull { param ->
        param.annotations.any { it.isPreviewParameter() }
      }
      func.annotations.findPreviews().distinct()
        .map { Pair(func, previewParam) }
    }.forEach { (func, previewParam) ->
      block(func, previewParam)
    }

  private fun CodeBlock.Builder.addError(
    function: KSFunctionDeclaration,
    snapshotName: String,
    buildErrorMessage: (String?) -> String
  ) {
    val qualifiedName = function.qualifiedName?.asString()

    addStatement("%L.PaparazziPreviewData.Error(", PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement("message = %S,", buildErrorMessage(qualifiedName))
    unindent()
    addStatement("),")
  }

  private fun CodeBlock.Builder.addDefault(
    function: KSFunctionDeclaration,
    snapshotName: String
  ) {
    addStatement("%L.PaparazziPreviewData.Default(", PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement("composable = { %L() },", function.qualifiedName?.asString())
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

  private fun checkVisibility(
    function: KSFunctionDeclaration
  ) = VisibilityCheck(
    isFunctionPrivate = function.getVisibility() == Visibility.PRIVATE
  )
}

internal data class VisibilityCheck(
  val isFunctionPrivate: Boolean
) {
  val isPrivate = isFunctionPrivate
}
