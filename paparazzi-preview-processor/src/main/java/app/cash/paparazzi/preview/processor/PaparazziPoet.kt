package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
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
      if (functions.count() == 0) {
        logger.info("No functions found with @Paparazzi annotation.")
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
    }

  @Suppress("SameParameterValue")
  private fun buildAnnotationsFile(fileName: String, propertyName: String, functions: Sequence<KSFunctionDeclaration>) =
    FileSpec.scriptBuilder(fileName, namespace)
      .addCode(
        buildCodeBlock {
          addStatement("internal val %L = listOf<%L.PaparazziPreviewData>(", propertyName, PREVIEW_RUNTIME_PACKAGE_NAME)
          indent()

          functions.process { func, preview, previewParam ->
            val snapshotName = func.snapshotName(namespace)
            val qualifiedName = func.qualifiedName?.asString()
            when {
              func.getVisibility() == Visibility.PRIVATE -> {
                logger.error("$qualifiedName is private. Make it internal or public to generate a snapshot.")
              }

              previewParam != null -> {
                addProvider(
                  function = func,
                  snapshotName = snapshotName,
                  preview = preview,
                  previewParam = previewParam
                )
              }

              else -> addDefault(
                function = func,
                preview = preview,
                snapshotName = snapshotName
              )
            }
          }

          unindent()
          add(")")
        }
      )
      .build()

  private fun Sequence<KSFunctionDeclaration>.process(
    block: (KSFunctionDeclaration, preview: PreviewModel, KSValueParameter?) -> Unit
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

  private fun CodeBlock.Builder.addDefault(
    function: KSFunctionDeclaration,
    preview: PreviewModel,
    snapshotName: String
  ) {
    addStatement("%L.PaparazziPreviewData.Default(", PREVIEW_RUNTIME_PACKAGE_NAME)
    indent()
    addStatement("snapshotName = %S,", snapshotName)
    addStatement("composable = { %L() },", function.qualifiedName?.asString())
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

private const val PREVIEW_RUNTIME_PACKAGE_NAME = "app.cash.paparazzi.preview.runtime"

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

@Suppress("UNCHECKED_CAST")
public fun <T> KSAnnotation.previewArg(name: String): T =
  arguments
    .first { it.name?.asString() == name }
    .let { it.value as T }

internal fun KSFunctionDeclaration.findDistinctPreviews() =
  annotations.findPreviews().toList()
    .map { preview ->
      PreviewModel(
        fontScale = preview.previewArg("fontScale"),
        device = preview.previewArg("device"),
        widthDp = preview.previewArg("widthDp"),
        heightDp = preview.previewArg("heightDp"),
        uiMode = preview.previewArg("uiMode"),
        locale = preview.previewArg("locale"),
        backgroundColor = preview.previewArg("backgroundColor"),
        showBackground = preview.previewArg("showBackground")
      )
    }
    .distinct()

internal fun KSFunctionDeclaration.previewParam() =
  parameters.firstOrNull { param ->
    param.annotations.any { it.isPreviewParameter() }
  }

internal fun KSValueParameter.previewParamProvider() =
  annotations
    .first { it.isPreviewParameter() }
    .arguments
    .first { arg -> arg.name?.asString() == "provider" }
    .let { it.value as KSType }
    .declaration

internal data class PreviewModel(
  val fontScale: Float,
  val device: String,
  val widthDp: Int,
  val heightDp: Int,
  val uiMode: Int,
  val locale: String,
  val backgroundColor: Long,
  val showBackground: Boolean
)

internal data class EnvironmentOptions(
  val namespace: String
)
