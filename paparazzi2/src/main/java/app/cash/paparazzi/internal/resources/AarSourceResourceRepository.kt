package app.cash.paparazzi.internal.resources

import android.annotation.SuppressLint
import app.cash.paparazzi.internal.resources.base.BasicResourceItem
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AndroidManifestPackageNameUtils
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility.PUBLIC
import com.android.utils.Base128InputStream
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import java.util.stream.Collectors

/**
 * A resource repository representing unpacked contents of a non-namespaced AAR.
 *
 * For performance reasons ID resources defined using @+id syntax in layout XML files are
 * obtained from R.txt instead, when it is available. This means that
 * [ResourceItem.getOriginalSource] method may return null for such ID resources.
 */
open class AarSourceResourceRepository(
  loader: RepositoryLoader<out AarSourceResourceRepository>,
  libraryName: String?
) : AbstractAarResourceRepository(loader.namespace, libraryName) {
  protected val resourceDirectoryOrFile: Path

  /**
   * Protocol used for constructing [PathString]s returned by the [BasicFileResourceItem.getSource] method.
   */
  private val sourceFileProtocol: String

  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the [BasicFileResourceItem.getSource] method.
   */
  private val resourcePathPrefix: String

  /**
   * Common prefix of URLs of all file resources. Used to compose resource URLs returned by
   * the [BasicFileResourceItem.getValue] method.
   */
  private val resourceUrlPrefix: String

  /** The package name read on-demand from the manifest.  */
  private val manifestPackageName: Lazy<String?>

  init {
    resourceDirectoryOrFile = loader.resourceDirectoryOrFile
    sourceFileProtocol = loader.sourceFileProtocol
    resourcePathPrefix = loader.resourcePathPrefix
    resourceUrlPrefix = loader.resourceUrlPrefix
    manifestPackageName = lazy {
      try {
        val manifestPath = getSourceFile("../$FN_ANDROID_MANIFEST_XML", true)
        return@lazy AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifestPath)
      } catch (e: FileNotFoundException) {
        return@lazy null
      } catch (e: IOException) {
        LOG.severe("Failed to read manifest $FN_ANDROID_MANIFEST_XML for $displayName: $e")
        return@lazy null
      }
    }
  }

  override val origin: Path
    get() = resourceDirectoryOrFile

  override fun getPackageName(): String? = namespace.packageName ?: manifestPackageName.value

  override fun getSourceFile(
    relativeResourcePath: String,
    forFileResource: Boolean
  ): PathString {
    return PathString(sourceFileProtocol, resourcePathPrefix + relativeResourcePath)
  }

  override fun getResourceUrl(relativeResourcePath: String): String =
    "$resourceUrlPrefix$relativeResourcePath"

  /**
   * Loads contents of the repository from the given input stream.
   */
  @Throws(IOException::class)
  fun loadFromStream(
    stream: Base128InputStream,
    stringCache: Map<String, String>,
    namespaceResolverCache: MutableMap<NamespaceResolver, NamespaceResolver>?
  ) = ResourceSerializationUtil.readResourcesFromStream(
    stream,
    stringCache,
    namespaceResolverCache,
    this,
    ::addResourceItem
  )

  // For debugging only.
  override fun toString(): String {
    return "${javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(this))} for $resourceDirectoryOrFile"
  }

  private class Loader(
    resourceDirectoryOrFile: Path,
    resourceFilesAndFolders: Collection<PathString>?,
    namespace: ResourceNamespace
  ) : RepositoryLoader<AarSourceResourceRepository>(resourceDirectoryOrFile, resourceFilesAndFolders, namespace) {
    private var rTxtIds: Set<String> = emptySet()

    @SuppressLint("NewApi")
    override fun loadIdsFromRTxt(): Boolean {
      if (zipFile == null) {
        val rDotTxt = resourceDirectoryOrFile.resolveSibling(FN_RESOURCE_TEXT)
        if (Files.exists(rDotTxt)) {
          try {
            val symbolTable = SymbolIo.readFromAaptNoValues(rDotTxt.toFile(), null)
            rTxtIds = computeIds(symbolTable)
            return true
          } catch (e: Exception) {
            LOG.warning("Failed to load id resources from $rDotTxt: $e")
          }
        }
      } else {
        val zipEntry = zipFile!!.getEntry(FN_RESOURCE_TEXT)
        if (zipEntry != null) {
          try {
            BufferedReader(
              InputStreamReader(zipFile!!.getInputStream(zipEntry), UTF_8)
            ).use { reader ->
              val symbolTable = SymbolIo.readFromAaptNoValues(
                reader,
                "$FN_RESOURCE_TEXT in $resourceDirectoryOrFile",
                null
              )
              rTxtIds = computeIds(symbolTable)
              return true
            }
          } catch (e: Exception) {
            LOG.warning(
              "Failed to load id resources from $FN_RESOURCE_TEXT in $resourceDirectoryOrFile: $e"
            )
          }
        }
        return false
      }
      return false
    }

    override fun finishLoading(repository: AarSourceResourceRepository) {
      super.finishLoading(repository)
      createResourcesForRTxtIds(repository)
    }

    /**
     * Creates ID resources for the ID names in the R.txt file.
     */
    private fun createResourcesForRTxtIds(repository: AarSourceResourceRepository) {
      if (rTxtIds.isNotEmpty()) {
        val configuration = getConfiguration(repository, ResourceItem.DEFAULT_CONFIGURATION)
        val sourceFile = ResourceSourceFileImpl(null, configuration)
        ResourceSourceFileImpl(null, configuration)
        for (name in rTxtIds) {
          addIdResourceItem(name, sourceFile)
        }
        addValueFileResources()
      }
    }

    @SuppressLint("NewApi")
    override fun loadPublicResourceNames() {
      if (zipFile == null) {
        val file = resourceDirectoryOrFile.resolveSibling(FN_PUBLIC_TXT)
        try {
          Files.newBufferedReader(file).use { reader -> readPublicResourceNames(reader) }
        } catch (e: NoSuchFileException) {
          // The "public.txt" file does not exist - defaultVisibility will be PUBLIC.
          defaultVisibility = PUBLIC
        } catch (e: IOException) {
          // Failure to load public resource names is not considered fatal.
          LOG.warning("Error reading $file: $e")
        }
      } else {
        val zipEntry = zipFile!!.getEntry(FN_PUBLIC_TXT)
        if (zipEntry == null) {
          // The "public.txt" file does not exist - defaultVisibility will be PUBLIC.
          defaultVisibility = PUBLIC
        } else {
          try {
            BufferedReader(
              InputStreamReader(
                zipFile!!.getInputStream(zipEntry),
                UTF_8
              )
            ).use { reader ->
              readPublicResourceNames(reader)
            }
          } catch (e: IOException) {
            // Failure to load public resource names is not considered fatal.
            LOG.warning("Error reading $FN_PUBLIC_TXT from $resourceDirectoryOrFile: $e")
          }
        }
      }
    }

    override fun addResourceItem(
      item: BasicResourceItem,
      repository: AarSourceResourceRepository
    ) = repository.addResourceItem(item)

    @Throws(IOException::class)
    private fun readPublicResourceNames(reader: BufferedReader) {
      var maybeLine: String?
      while (reader.readLine().also { maybeLine = it } != null) {
        var line = maybeLine!!
        // Lines in public.txt have the following format: <resource_type> <resource_name>
        line = line.trim { it <= ' ' }
        val delimiterPos = line.indexOf(' ')
        if (delimiterPos > 0 && delimiterPos + 1 < line.length) {
          val type = ResourceType.fromXmlTagName(line.substring(0, delimiterPos))
          if (type != null) {
            val name = line.substring(delimiterPos + 1)
            addPublicResourceName(type, name)
          }
        }
      }
    }
  }

  companion object {
    private fun computeIds(symbolTable: SymbolTable): Set<String> =
      symbolTable.symbols
        .row(ResourceType.ID)
        .values
        .stream()
        .map(Symbol::canonicalName)
        .collect(Collectors.toSet())

    private val LOG: Logger = Logger.getLogger(AarSourceResourceRepository::class.java.name)

    /**
     * Creates and loads a resource repository.
     *
     * @param resourceDirectoryOrFile the res directory or an AAR file containing resources
     * @param libraryName the name of the library
     * @return the created resource repository
     */
    fun create(resourceDirectoryOrFile: Path, libraryName: String): AarSourceResourceRepository =
      create(resourceDirectoryOrFile, null, ResourceNamespace.RES_AUTO, libraryName)

    /**
     * Creates and loads a resource repository.
     *
     * @param resourceFolderRoot specifies the resource files to be loaded. The list of files to be loaded can be restricted by providing
     * a not null `resourceFolderResources` list of files and subdirectories that should be loaded.
     * @param resourceFolderResources A null value indicates that all files and subdirectories in `resourceFolderRoot` should be loaded.
     * Otherwise files and subdirectories specified in `resourceFolderResources` are loaded.
     * @param libraryName the name of the library
     * @return the created resource repository
     */
    fun create(
      resourceFolderRoot: PathString,
      resourceFolderResources: Collection<PathString>?,
      libraryName: String
    ): AarSourceResourceRepository {
      val resDir = resourceFolderRoot.toPath()
      check(resDir != null)
      return create(resDir, resourceFolderResources, ResourceNamespace.RES_AUTO, libraryName)
    }

    private fun create(
      resourceDirectoryOrFile: Path,
      resourceFilesAndFolders: Collection<PathString>?,
      namespace: ResourceNamespace,
      libraryName: String
    ): AarSourceResourceRepository {
      val loader = Loader(resourceDirectoryOrFile, resourceFilesAndFolders, namespace)
      val repository = AarSourceResourceRepository(loader, libraryName)

      loader.loadRepositoryContents(repository)

      repository.populatePublicResourcesMap()
      repository.freezeResources()

      return repository
    }
  }
}
