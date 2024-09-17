package app.cash.paparazzi.internal.resources

import app.cash.paparazzi.internal.resources.base.BasicArrayResourceItem
import app.cash.paparazzi.internal.resources.base.BasicPluralsResourceItem
import app.cash.paparazzi.internal.resources.base.BasicTextValueResourceItem
import app.cash.paparazzi.internal.resources.base.BasicValueResourceItem
import app.cash.paparazzi.internal.resources.base.BasicValueResourceItemBase
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceValueMap
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.resources.ResourceType
import com.google.common.collect.Table

private const val DO_NOT_TRANSLATE = "donottranslate"

private val configCache = hashMapOf<Pair<ResourceSourceFile, LocaleQualifier>, RepositoryConfiguration>()

internal fun Table<ResourceNamespace, ResourceType, ResourceValueMap>.pseudolocalizeIfNeeded(
  localeQualifier: LocaleQualifier
): Table<ResourceNamespace, ResourceType, ResourceValueMap> {
  for (namespace in rowKeySet()) {
    for (type in columnKeySet()) {
      val resourceValues = this[namespace, type]!!
      resourceValues.forEach { (key, value) ->
        if (value !is BasicValueResourceItemBase || !value.isPseudolocalizable()) {
          return@forEach
        }

        val method = when (localeQualifier.value) {
          "en-rXA" -> Pseudolocalizer.Method.ACCENT
          "ar-rXB" -> Pseudolocalizer.Method.BIDI
          else -> return@forEach
        }

        val pseudoLocaleSourceFile = value.sourceFile.forLocale(localeQualifier)
        resourceValues[key] = when (value.resourceType) {
          ResourceType.STRING -> pseudolocalizeString(value as BasicValueResourceItem, pseudoLocaleSourceFile, method)
          ResourceType.PLURALS -> pseudolocalizePlural(value as BasicPluralsResourceItem, pseudoLocaleSourceFile, method)
          ResourceType.ARRAY -> pseudolocalizeArray(value as BasicArrayResourceItem, pseudoLocaleSourceFile, method)
          else -> return@forEach
        }
      }
    }
  }

  return this
}

/**
 * Currently, we cannot respect the "translatable" attribute from string resources as it is not stored in BasicValueResourceItemBase.
 * As a result, for now, untranslatable strings will be considered pseudolocalizable.
 * TODO: fix this
 */
private fun BasicValueResourceItemBase.isPseudolocalizable(): Boolean =
  sourceFile.configuration.folderConfiguration.localeQualifier == null && sourceFile.relativePath?.contains(DO_NOT_TRANSLATE) != true

private fun ResourceSourceFile.forLocale(localeQualifier: LocaleQualifier): ResourceSourceFile {
  val repositoryConfiguration = configCache.getOrPut(this to localeQualifier) {
    RepositoryConfiguration(
      repository = configuration.repository,
      folderConfiguration = FolderConfiguration.copyOf(configuration.folderConfiguration).apply {
        this.localeQualifier = localeQualifier
      }
    )
  }
  return when (this) {
    is ResourceSourceFileImpl -> ResourceSourceFileImpl(relativePath, repositoryConfiguration)
    is ResourceFile -> ResourceFile(file, repositoryConfiguration)
    // exhaustive to avoid potential breaking changes in future releases
    else -> throw IllegalStateException("${javaClass.name} is not handled for new RepositoryConfiguration")
  }
}

private fun pseudolocalizeString(
  original: BasicValueResourceItem,
  sourceFile: ResourceSourceFile,
  method: Pseudolocalizer.Method
): BasicValueResourceItem {
  val pseudoText = original.value?.pseudoLocalize(method)
  val pseudoItem = when (original) {
    is BasicTextValueResourceItem -> {
      val pseudoRawXml = (original as? BasicTextValueResourceItem)?.rawXmlValue?.pseudoLocalize(method)
      BasicTextValueResourceItem(
        type = original.type,
        name = original.name,
        sourceFile = sourceFile,
        visibility = original.visibility,
        textValue = pseudoText,
        rawXmlValue = pseudoRawXml
      )
    }
    else -> {
      BasicValueResourceItem(
        type = original.type,
        name = original.name,
        sourceFile = sourceFile,
        visibility = original.visibility,
        value = pseudoText
      )
    }
  }
  pseudoItem.namespaceResolver = original.namespaceResolver
  return pseudoItem
}

private fun pseudolocalizePlural(
  original: BasicPluralsResourceItem,
  sourceFile: ResourceSourceFile,
  method: Pseudolocalizer.Method
): BasicPluralsResourceItem {
  val pseudoValues = original.values.map { it.pseudoLocalize(method) }.toTypedArray()
  val pseudoItem = BasicPluralsResourceItem(
    name = original.name,
    sourceFile = sourceFile,
    visibility = original.visibility,
    quantityValues = original.arities.mapIndexed { index, arity -> arity to pseudoValues[index] }.toMap(),
    defaultArity = original.arities[original.defaultIndex]
  )
  pseudoItem.namespaceResolver = original.namespaceResolver
  return pseudoItem
}

private fun pseudolocalizeArray(
  original: BasicArrayResourceItem,
  sourceFile: ResourceSourceFile,
  method: Pseudolocalizer.Method
): BasicArrayResourceItem {
  val pseudoValues = original.toList().map { it.pseudoLocalize(method) }
  val pseudoItem = BasicArrayResourceItem(
    name = original.name,
    sourceFile = sourceFile,
    visibility = original.visibility,
    elements = pseudoValues,
    defaultIndex = original.defaultIndex
  )
  pseudoItem.namespaceResolver = original.namespaceResolver
  return pseudoItem
}

private fun String.pseudoLocalize(method: Pseudolocalizer.Method) =
  if (isBlank()) {
    // For empty string resources like <string name="config_inputEventCompatProcessorOverrideClassName" translatable="false"></string>,
    // we have to skip pseudolocalization as we are not unable to process the "translatable" attribute for now.
    // Otherwise the empty string will be incorrectly pseudolocalized to "[]", which is not a valid className in above example
    // The special handling for empty string is not necessary any more after we fix the "translatable" attribute issue
    this
  } else {
    with(Pseudolocalizer(method)) {
      start() + text(this@pseudoLocalize) + end()
    }
  }
