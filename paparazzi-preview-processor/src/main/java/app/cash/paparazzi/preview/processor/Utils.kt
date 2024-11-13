package app.cash.paparazzi.preview.processor

import com.google.devtools.ksp.symbol.FunctionKind.TOP_LEVEL
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

internal const val PACKAGE_NAME = "app.cash.paparazzi.annotations"

internal fun KSAnnotation.isPaparazzi() = qualifiedName() == "app.cash.paparazzi.annotations.Paparazzi"
internal fun KSAnnotation.isPreview() = qualifiedName() == "androidx.compose.ui.tooling.preview.Preview"
internal fun KSAnnotation.isPreviewParameter() =
  qualifiedName() == "androidx.compose.ui.tooling.preview.PreviewParameter"

internal fun KSAnnotation.qualifiedName() = declaration().qualifiedName?.asString() ?: ""
internal fun KSAnnotation.declaration() = annotationType.resolve().declaration

@Suppress("UNCHECKED_CAST")
public fun <T> KSAnnotation.previewArg(name: String): T =
  arguments
    .first { it.name?.asString() == name }
    .let { it.value as T }

internal fun Sequence<KSAnnotated>.findPaparazzi() =
  filterIsInstance<KSFunctionDeclaration>()
    .filter {
      it.annotations.hasPaparazzi() &&
        it.functionKind == TOP_LEVEL
    }

internal fun Sequence<KSAnnotation>.hasPaparazzi() = filter { it.isPaparazzi() }.count() > 0

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
