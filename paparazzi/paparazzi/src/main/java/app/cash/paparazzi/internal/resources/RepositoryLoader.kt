/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal.resources

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ATTR_FORMAT
import com.android.SdkConstants.ATTR_INDEX
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_QUANTITY
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.DOT_AAR
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.DOT_ZIP
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.PREFIX_THEME_REF
import com.android.SdkConstants.TAG_ATTR
import com.android.SdkConstants.TAG_EAT_COMMENT
import com.android.SdkConstants.TAG_ENUM
import com.android.SdkConstants.TAG_FLAG
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_RESOURCES
import com.android.SdkConstants.TAG_SKIP
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ArrayResourceValueImpl
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttrResourceValueImpl
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.DensityBasedResourceValue
import com.android.ide.common.rendering.api.DensityBasedResourceValueImpl
import com.android.ide.common.rendering.api.PluralsResourceValueImpl
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValueImpl
import com.android.ide.common.rendering.api.StyleableResourceValueImpl
import com.android.ide.common.rendering.api.TextResourceValueImpl
import com.android.ide.common.resources.ANDROID_AAPT_IGNORE
import com.android.ide.common.resources.AndroidAaptIgnore
import com.android.ide.common.resources.PatternBasedFileFilter
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ValueResourceNameValidator
import com.android.ide.common.resources.ValueXmlHelper
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.ide.common.util.toPathString
import com.android.io.CancellableFileIo
import com.android.resources.Arity
import com.android.resources.Density
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.VALUES
import com.android.resources.ResourceType
import com.android.resources.ResourceType.ANIMATOR
import com.android.resources.ResourceType.ARRAY
import com.android.resources.ResourceType.ATTR
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceType.INTERPOLATOR
import com.android.resources.ResourceType.LAYOUT
import com.android.resources.ResourceType.MENU
import com.android.resources.ResourceType.MIPMAP
import com.android.resources.ResourceType.PLURALS
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import com.android.resources.ResourceType.STYLEABLE
import com.android.resources.ResourceType.TRANSITION
import com.android.resources.ResourceVisibility
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import com.google.common.collect.Table
import com.google.common.collect.Tables
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque
import java.util.Deque
import java.util.EnumMap
import java.util.EnumSet
import java.util.logging.Logger
import java.util.zip.ZipFile

/**
 * Ported from: [RepositoryLoader.java](https://cs.android.com/android-studio/platform/tools/base/+/876aaf229c1e3b8144736d3338c628ba43ccac45:resource-repository/main/java/com/android/resources/base/RepositoryLoader.java)
 */
abstract class RepositoryLoader<T : LoadableResourceRepository>(
  val resourceDirectoryOrFile: Path,
  val resourceFilesAndFolders: Collection<PathString>?,
  val namespace: ResourceNamespace
) : FileFilter {
  private val fileFilter =
    PatternBasedFileFilter(AndroidAaptIgnore(System.getenv(ANDROID_AAPT_IGNORE)))

  private val publicResources: MutableMap<ResourceType, MutableSet<String>> =
    EnumMap(ResourceType::class.java)

  protected var defaultVisibility = ResourceVisibility.PRIVATE

  /** Cache of FolderConfiguration instances, keyed by qualifier strings (see [FolderConfiguration.getQualifierString]).  */
  protected val folderConfigCache = hashMapOf<String, FolderConfiguration>()
  private val configCache = hashMapOf<FolderConfiguration, RepositoryConfiguration>()
  private val parser = ValueResourceXmlParser()
  private val textExtractor = XmlTextExtractor()
  private val urlParser = ResourceUrlParser()

  // Used to keep track of resources defined in the current value resource file.
  private val valueFileResources: Table<ResourceType, String, BasicResourceItem> =
    Tables.newCustomTable(EnumMap(ResourceType::class.java)) { LinkedHashMap() }
  private val resourceDirectoryOrFilePath = PathString(resourceDirectoryOrFile)
  private val isLoadingFromZipArchive = isZipArchive(resourceDirectoryOrFile)

  protected var zipFile: ZipFile? = null

  open fun loadRepositoryContents(repository: T) {
    if (isLoadingFromZipArchive) {
      loadFromZip(repository)
    } else {
      loadFromResFolder(repository)
    }
  }

  protected open fun loadFromZip(repository: T) {
    try {
      ZipFile(resourceDirectoryOrFile.toFile()).use { zipFile ->
        this.zipFile = zipFile
        loadPublicResourceNames()
        val shouldParseResourceIds = !loadIdsFromRTxt()

        zipFile.stream().forEach { zipEntry ->
          if (!zipEntry.isDirectory) {
            val path = PathString(zipEntry.name)
            loadResourceFile(path, repository, shouldParseResourceIds)
          }
        }
      }
    } catch (e: Exception) {
      LOG.severe("Failed to load resources from $resourceDirectoryOrFile: $e")
    } finally {
      zipFile = null
    }

    finishLoading(repository)
  }

  protected open fun loadFromResFolder(repository: T) {
    try {
      if (CancellableFileIo.notExists(resourceDirectoryOrFile)) {
        return // Don't report errors if the resource directory doesn't exist. This happens in some tests.
      }

      loadPublicResourceNames()
      val shouldParseResourceIds = !loadIdsFromRTxt()

      val sourceFilesAndFolders =
        resourceFilesAndFolders?.map { it.toPath()!! } ?: listOf(resourceDirectoryOrFile)
      for (file in findResourceFiles(sourceFilesAndFolders)) {
        loadResourceFile(file, repository, shouldParseResourceIds)
      }
    } catch (e: Exception) {
      LOG.severe("Failed to load resources from $resourceDirectoryOrFile: $e")
    }

    finishLoading(repository)
  }

  protected fun loadResourceFile(
    file: PathString,
    repository: T,
    shouldParseResourceIds: Boolean
  ) {
    val folderName = file.parentFileName
    if (folderName != null) {
      val folderInfo = FolderInfo.create(folderName, folderConfigCache)
      if (folderInfo != null) {
        val configuration = getConfiguration(repository, folderInfo.configuration)
        loadResourceFile(file, folderInfo, configuration, shouldParseResourceIds)
      }
    }
  }

  protected open fun finishLoading(repository: T) = processAttrsAndStyleables()

  val sourceFileProtocol: String
    get() = if (isLoadingFromZipArchive) JAR_PROTOCOL else "file"

  val resourcePathPrefix: String
    get() = if (isLoadingFromZipArchive) {
      "${portableFileName(resourceDirectoryOrFile.toString())}${JAR_SEPARATOR}res/"
    } else {
      "${portableFileName(resourceDirectoryOrFile.toString())}/"
    }

  val resourceUrlPrefix: String
    get() = if (isLoadingFromZipArchive) {
      "$JAR_PROTOCOL://${portableFileName(resourceDirectoryOrFile.toString())}${JAR_SEPARATOR}res/"
    } else {
      "${portableFileName(resourceDirectoryOrFile.toString())}/"
    }

  /**
   * A hook for loading resource IDs from a R.txt file. This implementation does nothing but subclasses may override.
   *
   * @return true if the IDs were successfully loaded from R.txt
   */
  protected open fun loadIdsFromRTxt() = false

  override fun isIgnored(
    fileOrDirectory: Path,
    attrs: BasicFileAttributes
  ) = if (fileOrDirectory == resourceDirectoryOrFile) {
    false
  } else {
    fileFilter.isIgnored(fileOrDirectory.toString(), attrs.isDirectory)
  }

  protected open fun loadPublicResourceNames() {
    // todo load public resources
  }

  protected fun addPublicResourceName(type: ResourceType, name: String) {
    val names = publicResources.computeIfAbsent(type) { HashSet() }
    names += name
  }

  private fun findResourceFiles(filesOrFolders: List<Path>): List<PathString> {
    val fileCollector = ResourceFileCollector(this)
    for (file in filesOrFolders) {
      try {
        CancellableFileIo.walkFileTree(file, fileCollector)
      } catch (e: IOException) {
        // All IOExceptions are logged by ResourceFileCollector.
      }
    }
    for (e in fileCollector.ioErrors) {
      LOG.severe("Failed to load resources from $resourceDirectoryOrFile: $e")
    }
    fileCollector.resourceFiles.sort() // Make sure that the files are in canonical order.
    return fileCollector.resourceFiles
  }

  protected fun getConfiguration(
    repository: T,
    folderConfiguration: FolderConfiguration
  ): RepositoryConfiguration {
    var repositoryConfiguration = configCache[folderConfiguration]
    if (repositoryConfiguration != null) {
      return repositoryConfiguration
    }

    repositoryConfiguration = RepositoryConfiguration(repository, folderConfiguration!!)
    configCache[folderConfiguration] = repositoryConfiguration
    return repositoryConfiguration
  }

  private fun loadResourceFile(
    file: PathString,
    folderInfo: FolderInfo,
    configuration: RepositoryConfiguration,
    shouldParseResourceIds: Boolean
  ) {
    val folderType = getFolderType(file)
    if (folderType == VALUES) {
      if (isXmlFile(file)) {
        parseValueResourceFile(file, configuration)
      }
    } else {
      if (isXmlFile(file) && FolderTypeRelationship.isIdGeneratingFolderType(folderType)) {
        parseIdGeneratingResourceFile(file, configuration)
      }

      parseFileResourceFile(file, folderType, configuration)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun addResourceItem(item: BasicResourceItem) {
    addResourceItem(item, item.repository as T)
  }

  protected abstract fun addResourceItem(item: BasicResourceItem, repository: T)

  protected fun parseValueResourceFile(
    file: PathString,
    configuration: RepositoryConfiguration
  ) {
    try {
      getInputStream(file).use { stream ->
        parser.setInput(stream, null)
        var event: Int
        do {
          event = parser.nextToken()
          val depth = parser.depth
          if (event == XmlPullParser.START_TAG) {
            if (parser.prefix != null) {
              continue
            }
            val tagName = parser.name
            assert(depth <= 2) // Deeper tags should be consumed by the createResourceItem method.
            if (depth == 1) {
              if (tagName != TAG_RESOURCES) {
                break
              }
            } else if (depth > 1) {
              val resourceType = getResourceType(tagName, file)
              if (resourceType != null && resourceType != ResourceType.PUBLIC) {
                val resourceName = parser.getAttributeValue(null, ATTR_NAME)
                if (resourceName != null) {
                  validateResourceName(resourceName, resourceType, file)
                  val item = createResourceItem(resourceType, resourceName, file, configuration)
                  addValueResourceItem(item)
                } else {
                  // Skip the subtags when the tag of a valid resource type doesn't have a name.
                  skipSubTags()
                }
              } else {
                skipSubTags()
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT)
      }
    } // KXmlParser throws RuntimeException for an undefined prefix and an illegal attribute name.
    // todo these error hanldings are from AS, but should we actually throw the exception as illegal values should be fixed rather than swallowed
    catch (e: IOException) {
      handleParsingError(file, e)
    } catch (e: XmlPullParserException) {
      handleParsingError(file, e)
    } catch (e: XmlSyntaxException) {
      handleParsingError(file, e)
    } catch (e: RuntimeException) {
      handleParsingError(file, e)
    }
    addValueFileResources()
  }

  // todo attr and styleable needs special handlings
  // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:resource-repository/main/java/com/android/resources/base/RepositoryLoader.java;l=952?q=processAttrsAndStyleables&sq=&ss=android-studio%2Fplatform%2Ftools%2Fbase
  private fun addValueResourceItem(item: BasicResourceItem) {
    // Add attr and styleable resources to intermediate maps to post-process them in the processAttrsAndStyleables
    // method after all resources are loaded.
    when (val resourceType: ResourceType = item.type) {
      // ATTR -> {
      //   // addAttr(item as BasicAttrResourceItem, myAttrs)
      // }
      //
      // STYLEABLE -> {
      //   // myStyleables.put(item.getName(), item as BasicStyleableResourceItem)
      // }

      else -> {
        // For compatibility with resource merger code we add value resources first to a file-specific map,
        // then move them to the global resource table. In case when there are multiple definitions of
        // the same resource in a single XML file, this algorithm preserves only the last definition.
        valueFileResources.put(resourceType, item.name, item)
      }
    }
  }

  protected fun addValueFileResources() {
    for (item in valueFileResources.values()) {
      addResourceItem(item)
    }
    valueFileResources.clear()
  }

  protected fun parseIdGeneratingResourceFile(
    file: PathString,
    configuration: RepositoryConfiguration
  ) {
    try {
      getInputStream(file).use { stream ->
        val parser = KXmlParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(stream, null)
        var event: Int
        do {
          event = parser.nextToken()
          if (event == XmlPullParser.START_TAG) {
            val numAttributes = parser.attributeCount
            for (i in 0 until numAttributes) {
              val idValue = parser.getAttributeValue(i)
              if (idValue.startsWith(NEW_ID_PREFIX) && idValue.length > NEW_ID_PREFIX.length) {
                val resourceName = idValue.substring(NEW_ID_PREFIX.length)
                addIdResourceItem(resourceName, configuration)
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT)
      }
    } // KXmlParser throws RuntimeException for an undefined prefix and an illegal attribute name.
    catch (e: IOException) {
      handleParsingError(file, e)
    } catch (e: XmlPullParserException) {
      handleParsingError(file, e)
    } catch (e: java.lang.RuntimeException) {
      handleParsingError(file, e)
    }
    addValueFileResources()
  }

  protected open fun handleParsingError(file: PathString, e: java.lang.Exception) {
    LOG.warning("Failed to parse $file: $e")
  }

  @Throws(IOException::class)
  protected open fun getInputStream(file: PathString): InputStream {
    return if (zipFile == null) {
      val path = file.toPath()
      check(path != null)
      BufferedInputStream(CancellableFileIo.newInputStream(path))
    } else {
      val entry = zipFile!!.getEntry(file.portablePath)
        ?: throw NoSuchFileException(file.portablePath)
      BufferedInputStream(zipFile!!.getInputStream(entry))
    }
  }

  protected fun addIdResourceItem(
    resourceName: String,
    repositoryConfiguration: RepositoryConfiguration
  ) {
    val visibility = getVisibility(ID, resourceName)
    val resourceValue =
      ResourceValueImpl(repositoryConfiguration.repository.namespace, ID, resourceName, null)
    val item =
      BasicResourceItem(ID, resourceName, visibility, repositoryConfiguration, resourceValue)
    addValueResourceItem(item)
  }

  private fun createFileResourceItem(
    file: PathString,
    name: String,
    resourceType: ResourceType,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val visibility = getVisibility(resourceType, name)
    var density: Density? = null
    if (DensityBasedResourceValue.isDensityBasedResourceType(resourceType)) {
      val densityQualifier =
        configuration.folderConfiguration.densityQualifier
      if (densityQualifier != null) {
        density = densityQualifier.value
      }
    }
    return createFileResourceItem(
      file,
      resourceType,
      name,
      configuration,
      visibility,
      density
    )
  }

  protected fun createFileResourceItem(
    file: PathString,
    type: ResourceType,
    name: String,
    configuration: RepositoryConfiguration,
    visibility: ResourceVisibility,
    density: Density?
  ): BasicResourceItem {
    val resourceValue = if (density != null) {
      DensityBasedResourceValueImpl(
        configuration.repository.namespace,
        type,
        name,
        file.nativePath,
        density,
        null
      )
    } else {
      ResourceValueImpl(configuration.repository.namespace, type, name, file.nativePath, null)
    }

    return BasicResourceItem(type, name, visibility, configuration, resourceValue)
  }

  @Throws(
    IOException::class,
    XmlPullParserException::class,
    XmlSyntaxException::class
  )
  private fun createResourceItem(
    type: ResourceType,
    name: String,
    file: PathString,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    return when (type) {
      ARRAY -> createArrayItem(name, file, configuration)
      ATTR -> createAttrItem(name, file, configuration)
      PLURALS -> createPluralsItem(name, file, configuration)
      STRING -> createStringItem(type, name, configuration, true)
      STYLE -> createStyleItem(name, configuration)
      STYLEABLE -> createStyleableItem(name, file, configuration)
      ANIMATOR, DRAWABLE, INTERPOLATOR, LAYOUT, MENU, MIPMAP, TRANSITION -> createFileReferenceItem(
        type,
        name,
        configuration
      )

      else -> createStringItem(type, name, configuration, false)
    }
  }

  @Throws(
    IOException::class,
    XmlPullParserException::class,
    XmlSyntaxException::class
  )
  private fun createArrayItem(
    name: String,
    file: PathString,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val namespaceResolver = parser.namespaceResolver
    val resourceValue = ArrayResourceValueImpl(
      configuration.repository.namespace,
      name,
      configuration.repository.libraryName
    )
    val indexValue = parser.getAttributeValue(TOOLS_URI, ATTR_INDEX)
    forSubTags(TAG_ITEM) {
      val text = textExtractor.extractText(parser, false)
      resourceValue.addElement(text)
    }
    val index: Int
    if (indexValue != null) {
      index = try {
        Integer.parseUnsignedInt(indexValue)
      } catch (e: NumberFormatException) {
        throw XmlSyntaxException(
          "The value of the " + namespaceResolver.prefixToUri(TOOLS_URI) + ':' + ATTR_INDEX + " attribute is not a valid number.",
          parser,
          file.fileName
        )
      }
      if (index >= resourceValue.elementCount) {
        throw XmlSyntaxException(
          "The value of the " + namespaceResolver.prefixToUri(TOOLS_URI) + ':' + ATTR_INDEX + " attribute is out of bounds.",
          parser,
          file.fileName
        )
      }
    }
    val visibility = getVisibility(ARRAY, name)
    resourceValue.namespaceResolver = namespaceResolver
    return BasicResourceItem(type = ARRAY, name, visibility, configuration, resourceValue)
  }

  @Throws(
    IOException::class,
    XmlPullParserException::class,
    XmlSyntaxException::class
  )
  private fun createAttrItem(
    name: String,
    file: PathString,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val namespaceResolver = parser.namespaceResolver
    val resourceValue = AttrResourceValueImpl(
      configuration.repository.namespace,
      name,
      configuration.repository.libraryName
    )

    val attrNamespace: ResourceNamespace?
    urlParser.parseResourceUrl(name)
    if (urlParser.hasNamespacePrefix(ANDROID_NS_NAME)) {
      attrNamespace = ResourceNamespace.ANDROID
    } else {
      val prefix = urlParser.namespacePrefix
      attrNamespace =
        ResourceNamespace.fromNamespacePrefix(prefix, namespace, parser.namespaceResolver)
      if (attrNamespace == null) {
        throw XmlSyntaxException(
          "Undefined prefix of attr resource name \"$name\"",
          parser,
          getDisplayName(file)
        )
      }
    }
    val formatString = parser.getAttributeValue(null, ATTR_FORMAT)
    val formats = if (formatString.isNullOrBlank()) {
      EnumSet.noneOf(AttributeFormat::class.java)
    } else {
      AttributeFormat.parse(formatString)
    }

    // The average number of enum or flag values is 7 for Android framework, so start with small maps.
    forSubTags(null) {
      if (parser.prefix == null) {
        val tagName = parser.name
        val format =
          if (tagName == TAG_ENUM) AttributeFormat.ENUM else if (tagName == TAG_FLAG) AttributeFormat.FLAGS else null
        if (format != null) {
          formats += format
          val valueName = parser.getAttributeValue(null, ATTR_NAME)
          if (valueName != null) {
            val value = parser.getAttributeValue(null, ATTR_VALUE)
            var numericValue: Int? = null
            if (value != null) {
              try {
                // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we use Long.decode instead.
                numericValue = java.lang.Long.decode(value).toInt()
              } catch (ignored: NumberFormatException) {
              }
            }
            resourceValue.addValue(valueName, numericValue, "")
          }
        }
      }
    }

    resourceValue.setFormats(formats)
    resourceValue.namespaceResolver = namespaceResolver
    val visibility = getVisibility(ATTR, name)

    // todo kzheng handle attrNamespace
    return BasicResourceItem(type = ATTR, name, visibility, configuration, resourceValue)
  }

  @Throws(
    IOException::class,
    XmlPullParserException::class,
    XmlSyntaxException::class
  )
  private fun createPluralsItem(
    name: String,
    file: PathString,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val resourceValue = object : PluralsResourceValueImpl(namespace, name, null, null) {
      // Allow the user to specify a specific quantity to use via tools:quantity
      override fun getValue(): String {
        val quantity = parser.getAttributeValue(
          ATTR_QUANTITY,
          TOOLS_URI
        )
        if (quantity != null) {
          val value = getValue(quantity)
          if (value != null) {
            return value
          }
        }
        return super.getValue()
      }
    }
    val defaultQuantity = parser.getAttributeValue(TOOLS_URI, ATTR_QUANTITY)
    val namespaceResolver = parser.namespaceResolver
    forSubTags(TAG_ITEM) {
      val quantityValue = parser.getAttributeValue(null, ATTR_QUANTITY)
      if (quantityValue != null) {
        val quantity = Arity.getEnum(quantityValue)
        if (quantity != null) {
          val text = textExtractor.extractText(parser, false)
          resourceValue.addPlural(quantityValue, text)
        }
      }
    }
    if (defaultQuantity != null) {
      val defaultArity = Arity.getEnum(defaultQuantity)
      if (defaultArity == null || resourceValue.getValue(defaultArity.name) == null) {
        throw XmlSyntaxException(
          "Invalid value of the " + namespaceResolver.prefixToUri(TOOLS_URI) + ':' + ATTR_QUANTITY + " attribute.",
          parser,
          getDisplayName(file)
        )
      }
    }
    val visibility = getVisibility(PLURALS, name)
    resourceValue.namespaceResolver = namespaceResolver
    return BasicResourceItem(type = PLURALS, name, visibility, configuration, resourceValue)
  }

  @Throws(IOException::class, XmlPullParserException::class)
  private fun createStringItem(
    type: ResourceType,
    name: String,
    configuration: RepositoryConfiguration,
    withRowXml: Boolean
  ): BasicResourceItem {
    val namespaceResolver = parser.namespaceResolver
    val text = if (type == ResourceType.ID) null else textExtractor.extractText(parser, withRowXml)
    val rawXml = if (type == ResourceType.ID) null else textExtractor.getRawXml()
    assert(withRowXml || rawXml == null) // Text extractor doesn't extract raw XML unless asked to do it.
    val visibility = getVisibility(type, name)
    val resourceValue = if (rawXml == null) {
      TextResourceValueImpl(namespace, name, null, null, null).apply {
        value = text
      }
    } else {
      ResourceValueImpl(namespace, type, name, null, null)
    }
    resourceValue.namespaceResolver = namespaceResolver
    return BasicResourceItem(type, name, visibility, configuration, resourceValue)
  }

  @Throws(IOException::class, XmlPullParserException::class)
  private fun createStyleItem(
    name: String,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val namespaceResolver = parser.namespaceResolver
    var parentStyle = parser.getAttributeValue(null, ATTR_PARENT)
    if (parentStyle != null && parentStyle.isNotEmpty()) {
      urlParser.parseResourceUrl(parentStyle)
      parentStyle = urlParser.qualifiedName
    }

    val resourceValue = StyleResourceValueImpl(namespace, name, parentStyle, null)
    resourceValue.namespaceResolver = namespaceResolver

    forSubTags(TAG_ITEM) {
      val itemNamespaceResolver = parser.namespaceResolver
      val itemName = parser.getAttributeValue(null, ATTR_NAME)
      if (itemName != null) {
        val text = textExtractor.extractText(parser, false)
        val styleItem = StyleItemResourceValueImpl(
          namespace,
          itemName,
          text,
          configuration.repository.libraryName
        )
        styleItem.namespaceResolver = itemNamespaceResolver
        resourceValue.addItem(styleItem)
      }
    }

    val visibility = getVisibility(STYLE, name)
    return BasicResourceItem(STYLE, name, visibility, configuration, resourceValue)
  }

  @Throws(IOException::class, XmlPullParserException::class)
  private fun createStyleableItem(
    name: String,
    file: PathString,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val namespaceResolver = parser.namespaceResolver
    val resourceValue = StyleableResourceValueImpl(namespace, name, null, null)
    forSubTags(TAG_ATTR) {
      val attrName = parser.getAttributeValue(null, ATTR_NAME)
      if (attrName != null) {
        try {
          val item = createAttrItem(attrName, file, configuration)
          val value = item.resourceValue as AttrResourceValue
          resourceValue.addValue(value)

          if (item.namespace == namespace && (namespace != ResourceNamespace.RES_AUTO || value.formats.isNotEmpty())) {
            addValueResourceItem(item)
          }
        } catch (e: XmlSyntaxException) {
          handleParsingError(file, e)
        }
      }
    }

    // AAPT2 treats all styleable resources as public.
    // See https://android.googlesource.com/platform/frameworks/base/+/master/tools/aapt2/ResourceParser.cpp#1539
    resourceValue.namespaceResolver = namespaceResolver
    return BasicResourceItem(
      STYLEABLE,
      name,
      ResourceVisibility.PUBLIC,
      configuration,
      resourceValue
    )
  }

  /**
   * Adds attr definitions from [attrs], and attr definition candidates from [attrCandidates]
   * if they don't match the attr definitions present in [attrs].
   */
  private fun processAttrsAndStyleables() {
    // for (attr in myAttrs.values()) {
    //   addAttrWithAdjustedFormats(attr)
    // }
    // for (attr in myAttrCandidates.values()) {
    //   val attrs: List<BasicAttrResourceItem> = myAttrs.get(attr.getName())
    //   val i: Int = findResourceWithSameNameAndConfiguration(attr, attrs)
    //   if (i < 0) {
    //     addAttrWithAdjustedFormats(attr)
    //   }
    // }
    //
    // // Resolve attribute references where it can be done without loosing any data to reduce resource memory footprint.
    // for (styleable in myStyleables.values()) {
    //   addResourceItem(resolveAttrReferences(styleable))
    // }
  }

  @Throws(IOException::class, XmlPullParserException::class)
  private fun createFileReferenceItem(
    type: ResourceType,
    name: String,
    configuration: RepositoryConfiguration
  ): BasicResourceItem {
    val namespaceResolver = parser.namespaceResolver
    var text = textExtractor.extractText(parser, false).trim()
    if (text.isNotEmpty() && !text.startsWith(PREFIX_RESOURCE_REF) && !text.startsWith(
        PREFIX_THEME_REF
      )
    ) {
      text = text.replace('/', File.separatorChar)
    }
    val visibility = getVisibility(type, name)
    val resourceValue = ResourceValueImpl(namespace, type, name, text, null)
    resourceValue.namespaceResolver = namespaceResolver
    return BasicResourceItem(type, name, visibility, configuration, resourceValue)
  }

  @Throws(XmlSyntaxException::class)
  private fun getResourceType(
    tagName: String,
    file: PathString
  ): ResourceType? {
    var type = ResourceType.fromXmlTagName(tagName)
    if (type == null) {
      if (TAG_EAT_COMMENT == tagName || TAG_SKIP == tagName) {
        return null
      }
      if (tagName == TAG_ITEM) {
        val typeAttr = parser.getAttributeValue(null, ATTR_TYPE)
        if (typeAttr != null) {
          type = ResourceType.fromClassName(typeAttr)
          if (type != null) {
            return type
          }

          LOG.warning("Unrecognized type attribute \"$typeAttr\" at ${getDisplayName(file)} line ${parser.lineNumber}")
        }
      } else {
        LOG.warning("Unrecognized tag name \"$tagName\" at ${getDisplayName(file)} line ${parser.lineNumber}")
      }
    }

    return type
  }

  /**
   * If `tagName` is null, calls `subtagVisitor.visitTag()` for every subtag of the current tag.
   * If `tagName` is not null, calls `subtagVisitor.visitTag()` for every subtag of the current tag
   * which name doesn't have a prefix and matches `tagName`.
   */
  @Throws(
    IOException::class,
    XmlPullParserException::class
  )
  private fun forSubTags(tagName: String?, subtagVisitor: XmlTagVisitor) {
    val elementDepth: Int = parser.depth
    var event: Int
    do {
      event = parser.nextToken()
      if (event == XmlPullParser.START_TAG && (tagName == null || tagName == parser.name && parser.prefix == null)) {
        subtagVisitor.visitTag()
      }
    } while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.getDepth() > elementDepth))
  }

  /**
   * Skips all subtags of the current tag. When the method returns, the parser is positioned at the end tag
   * of the current element.
   */
  @Throws(
    IOException::class,
    XmlPullParserException::class
  )
  private fun skipSubTags() {
    val elementDepth: Int = parser.depth
    var event: Int
    do {
      event = parser.nextToken()
    } while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.depth > elementDepth))
  }

  @Throws(XmlSyntaxException::class)
  private fun validateResourceName(
    resourceName: String,
    resourceType: ResourceType,
    file: PathString
  ) {
    val error = ValueResourceNameValidator.getErrorText(resourceName, resourceType)
    if (error != null) {
      throw XmlSyntaxException(error, parser, file.nativePath)
    }
  }

  private fun getDisplayName(file: PathString) = file.nativePath

  private fun getDisplayName(file: File) = getDisplayName(file.toPathString())

  protected fun getVisibility(
    resourceType: ResourceType,
    resourceName: String
  ): ResourceVisibility {
    // todo look up public resources to return proper visibility
    return ResourceVisibility.PRIVATE
    // val names: Set<String> = myPublicResources.get(resourceType)
    // return if (names != null && names.contains(getKeyForVisibilityLookup(resourceName))) ResourceVisibility.PUBLIC else myDefaultVisibility
  }

  private fun parseFileResourceFile(
    file: PathString,
    folderType: ResourceFolderType,
    configuration: RepositoryConfiguration
  ) {
    val resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(folderType)
    val resourceName = SdkUtils.fileNameToResourceName(file.fileName)
    val item = createFileResourceItem(
      file,
      resourceName,
      resourceType,
      configuration
    )
    addResourceItem(item, item.repository as T)
  }

  private fun getFolderType(file: PathString) =
    ResourceFolderType.getFolderType(file.parentFileName)

  private fun interface XmlTagVisitor {
    /** Is called when the parser is positioned at a [XmlPullParser.START_TAG].  */
    @Throws(IOException::class, XmlPullParserException::class)
    fun visitTag()
  }

  /**
   * Information about a resource folder.
   */
  protected class FolderInfo private constructor(
    val folderType: ResourceFolderType,
    val configuration: FolderConfiguration,
    val resourceType: ResourceType?,
    val isIdGenerating: Boolean
  ) {
    companion object {
      /**
       * Returns a FolderInfo for the given folder name.
       *
       * @param folderName the name of a resource folder
       * @param folderConfigCache the cache of FolderConfiguration objects keyed by qualifier strings
       * @return the FolderInfo object, or null if folderName is not a valid name of a resource folder
       */
      fun create(
        folderName: String,
        folderConfigCache: MutableMap<String, FolderConfiguration>
      ): FolderInfo? {
        val folderType =
          ResourceFolderType.getFolderType(folderName) ?: return null
        val qualifier = FolderConfiguration.getQualifier(folderName)
        val config = folderConfigCache.computeIfAbsent(
          qualifier
        ) { qualifierString: String? ->
          FolderConfiguration.getConfigForQualifierString(
            qualifierString
          )
        } ?: return null
        config.normalizeByRemovingRedundantVersionQualifier()
        val resourceType: ResourceType?
        val isIdGenerating: Boolean
        if (folderType == VALUES) {
          resourceType = null
          isIdGenerating = false
        } else {
          resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(folderType)
          isIdGenerating = FolderTypeRelationship.isIdGeneratingFolderType(folderType)
        }
        return FolderInfo(folderType, config, resourceType, isIdGenerating)
      }
    }
  }

  private class ResourceFileCollector(val fileFilter: FileFilter) : FileVisitor<Path> {
    val resourceFiles = mutableListOf<PathString>()
    val ioErrors = mutableListOf<IOException>()

    override fun preVisitDirectory(
      dir: Path,
      attrs: BasicFileAttributes
    ) = if (fileFilter.isIgnored(dir, attrs)) {
      FileVisitResult.SKIP_SUBTREE
    } else {
      FileVisitResult.CONTINUE
    }

    override fun visitFile(
      file: Path,
      attrs: BasicFileAttributes
    ): FileVisitResult {
      if (fileFilter.isIgnored(file, attrs)) {
        return FileVisitResult.SKIP_SUBTREE
      }
      resourceFiles += PathString(file)
      return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
      ioErrors += exc
      return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(
      dir: Path,
      exc: IOException?
    ) = FileVisitResult.CONTINUE
  }

  private class XmlTextExtractor {
    private val text = StringBuilder()
    private val rawXml = StringBuilder()
    private val textInclusionState: Deque<Boolean> = ArrayDeque()
    private var nontrivialRawXml = false

    @Throws(IOException::class, XmlPullParserException::class)
    fun extractText(parser: XmlPullParser, withRawXml: Boolean): String {
      text.setLength(0)
      rawXml.setLength(0)
      textInclusionState.clear()
      nontrivialRawXml = false
      val elementDepth = parser.depth
      var event: Int
      loop@ do {
        event = parser.nextToken()
        when (event) {
          XmlPullParser.START_TAG -> {
            val tagName = parser.name
            if (ResourceItem.XLIFF_G_TAG == tagName && isXliffNamespace(parser.namespace)) {
              var includeNestedText = getTextInclusionState()
              val example = parser.getAttributeValue(null, ResourceItem.ATTR_EXAMPLE)
              if (example != null) {
                text.append('(').append(example).append(')')
                includeNestedText = false
              } else {
                val id = parser.getAttributeValue(null, SdkConstants.ATTR_ID)
                if (id != null && id != "id") {
                  text.append('$').append('{').append(id).append('}')
                  includeNestedText = false
                }
              }
              textInclusionState.addLast(includeNestedText)
            }
            if (withRawXml) {
              nontrivialRawXml = true
              rawXml.append('<')
              val prefix = parser.prefix
              if (prefix != null) {
                rawXml.append(prefix).append(':')
              }
              rawXml.append(tagName)
              val numAttr = parser.attributeCount
              var i = 0
              while (i < numAttr) {
                rawXml.append(' ')
                val attributePrefix = parser.getAttributePrefix(i)
                if (attributePrefix != null) {
                  rawXml.append(attributePrefix).append(':')
                }
                rawXml.append(parser.getAttributeName(i)).append('=').append('"')
                XmlUtils.appendXmlAttributeValue(rawXml, parser.getAttributeValue(i))
                rawXml.append('"')
                i++
              }
              rawXml.append('>')
            }
          }

          XmlPullParser.END_TAG -> {
            if (parser.depth <= elementDepth) {
              break@loop
            }
            val tagName = parser.name
            if (withRawXml) {
              rawXml.append('<').append('/')
              val prefix = parser.prefix
              if (prefix != null) {
                rawXml.append(prefix).append(':')
              }
              rawXml.append(tagName).append('>')
            }
            if (ResourceItem.XLIFF_G_TAG == tagName && isXliffNamespace(parser.namespace)) {
              textInclusionState.removeLast()
            }
          }

          XmlPullParser.ENTITY_REF, XmlPullParser.TEXT -> {
            val textPiece = parser.text
            if (getTextInclusionState()) {
              text.append(textPiece)
            }
            if (withRawXml) {
              rawXml.append(textPiece)
            }
          }

          XmlPullParser.CDSECT -> {
            val textPiece = parser.text
            if (getTextInclusionState()) {
              text.append(textPiece)
            }
            if (withRawXml) {
              nontrivialRawXml = true
              rawXml.append("<![CDATA[").append(textPiece).append("]]>")
            }
          }
        }
      } while (event != XmlPullParser.END_DOCUMENT)

      return ValueXmlHelper.unescapeResourceString(text.toString(), false, true)
    }

    private fun getTextInclusionState(): Boolean =
      textInclusionState.isEmpty() || textInclusionState.last

    fun getRawXml(): String? = if (nontrivialRawXml) rawXml.toString() else null

    companion object {
      private fun isXliffNamespace(namespaceUri: String?) =
        namespaceUri?.startsWith(ResourceItem.XLIFF_NAMESPACE_PREFIX) ?: false
    }
  }

  private class XmlSyntaxException(error: String, parser: XmlPullParser, filename: String) :
    Exception(error + " at " + filename + " line " + parser.lineNumber)

  companion object {
    private val LOG: Logger = Logger.getLogger(RepositoryLoader::class.java.name)
    const val JAR_PROTOCOL = "jar"
    const val JAR_SEPARATOR = "!/"

    private fun isXmlFile(file: PathString) = isXmlFile(file.fileName)

    private fun isXmlFile(filename: String) = SdkUtils.endsWithIgnoreCase(filename, DOT_XML)

    private fun isZipArchive(resourceDirectoryOrFile: Path): Boolean {
      val filename = resourceDirectoryOrFile.fileName.toString()
      return SdkUtils.endsWithIgnoreCase(filename, DOT_AAR) ||
        SdkUtils.endsWithIgnoreCase(filename, DOT_JAR) ||
        SdkUtils.endsWithIgnoreCase(filename, DOT_ZIP)
    }

    fun portableFileName(fileName: String): String = fileName.replace(File.separatorChar, '/')
  }
}
