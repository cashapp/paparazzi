package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.buildCodeBlock

internal class PaparazziPoet(
  private val logger: KSPLogger,
  private val namespace: String
) {
  fun buildFiles(functions: Sequence<KSFunctionDeclaration>, isTest: Boolean) =
    if (isTest) {
      emptyList()
    } else {
      listOf(
        buildAnnotationsFile(
          fileName = "PaparazziPreviews",
          propertyName = "paparazziPreviews",
          functions = functions
        )
      )
    }

  @Suppress("SameParameterValue")
  private fun buildAnnotationsFile(fileName: String, propertyName: String, functions: Sequence<KSFunctionDeclaration>) =
    FileSpec.scriptBuilder(fileName, namespace)
      .addCode(
        buildCodeBlock {
          addStatement("internal val %L = listOf<%L.PaparazziPreviewData>(", propertyName, PACKAGE_NAME)
          indent()

          if (functions.count() == 0) {
            addEmpty()
          } else {
            functions.process { func, previewParam ->
              val snapshotName = func.snapshotName(namespace)

              when {
                func.getVisibility() == Visibility.PRIVATE -> addError(
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
          add(")")
        }
      )
      .build()

  private fun CodeBlock.Builder.addEmpty() {
    addStatement("%L.PaparazziPreviewData.Empty,", PACKAGE_NAME)
  }

  private fun Sequence<KSFunctionDeclaration>.process(block: (KSFunctionDeclaration, KSValueParameter?) -> Unit) =
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

  private fun CodeBlock.Builder.addDefault(function: KSFunctionDeclaration, snapshotName: String) {
    addStatement("%L.PaparazziPreviewData.Default(", PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement("composable = { %L() },", function.qualifiedName?.asString())
    unindent()
    addStatement("),")
  }

  private fun KSFunctionDeclaration.snapshotName(namespace: String) =
    buildList {
      with(containingFile!!) {
        add(
          "${packageName.asString()}.${fileName.removeSuffix(".kt")}"
            .removePrefix("$namespace.")
            .replace(".", "_")
        )
      }
      add(simpleName.asString())
    }.joinToString("_")
}

private const val PACKAGE_NAME = "app.cash.paparazzi.annotations"

internal fun KSAnnotation.isPreview() = qualifiedName() == "androidx.compose.ui.tooling.preview.Preview"
internal fun KSAnnotation.isPreviewParameter() =
  qualifiedName() == "androidx.compose.ui.tooling.preview.PreviewParameter"

internal fun KSAnnotation.qualifiedName() = declaration().qualifiedName?.asString() ?: ""
internal fun KSAnnotation.declaration() = annotationType.resolve().declaration

/**
 * when the same annotations are applied higher in the tree, an endless recursive lookup can occur.
 * using a stack to keep to a record of each symbol lets us break when we hit one we've already encountered
 */
internal fun Sequence<KSAnnotation>.findPreviews(stack: Set<KSAnnotation> = setOf()): Sequence<KSAnnotation> {
  val direct = filter { it.isPreview() }
  val indirect = filterNot { it.isPreview() || stack.contains(it) }
    .map { it.declaration().annotations.findPreviews(stack.plus(it)) }
    .flatten()
  return direct.plus(indirect)
}
