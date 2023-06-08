package app.cash.paparazzi.internal.resources

import android.annotation.SuppressLint
import app.cash.paparazzi.internal.resources.base.BasicFileResourceItem
import app.cash.paparazzi.internal.resources.base.BasicResourceItem
import app.cash.paparazzi.internal.resources.base.BasicValueResourceItemBase
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.ResourceVisitor.VisitResult
import com.android.ide.common.resources.ResourceVisitor.VisitResult.ABORT
import com.android.ide.common.resources.ResourceVisitor.VisitResult.CONTINUE
import com.android.ide.common.util.PathString
import com.android.resources.ResourceFolderType.VALUES
import com.android.resources.ResourceType
import com.android.utils.SdkUtils
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.ListMultimap
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumMap
import java.util.logging.Logger
import javax.lang.model.SourceVersion.isIdentifier
import javax.lang.model.SourceVersion.isKeyword
import kotlin.io.path.exists

/**
 * The [ResourceFolderRepository] is a leaf in the repository tree, and is used for user editable
 * resources (e.g. the resources in the project, typically the res/main source set.)
 *
 * Each [ResourceFolderRepository] contains the resources provided by a single res folder.
 */
@SuppressLint("NewApi")
class ResourceFolderRepository(
  val resourceDir: File,
  private val namespace: ResourceNamespace
) : LocalResourceRepository(resourceDir.name), LoadableResourceRepository {
  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the [BasicFileResourceItem.getSource] method.
   */
  private val resourcePathPrefix: String = "${resourceDir.path}/"

  /**
   * Same as [resourcePathPrefix] but in a form of [PathString].  Used to produce
   * resource paths returned by the [BasicResourceItem.getOriginalSource] method.
   */
  private val resourcePathBase: PathString = PathString(resourcePathPrefix)

  private val resourceTable =
    EnumMap<ResourceType, ListMultimap<String, ResourceItem>>(ResourceType::class.java)

  init {
    Loader(this).load()
  }

  override val libraryName: String?
    get() = null // Resource folder is not a library.

  override val origin: Path
    get() = Paths.get(resourceDir.path)

  override fun getResourceUrl(relativeResourcePath: String): String =
    "$resourcePathPrefix$relativeResourcePath"

  override fun getSourceFile(
    relativeResourcePath: String,
    forFileResource: Boolean
  ): PathString = resourcePathBase.resolve(relativeResourcePath)

  override fun getPackageName(): String? = namespace.packageName

  override fun containsUserDefinedResources(): Boolean = true

  /**
   * Inserts the given resources into this repository.
   */
  private fun commitToRepository(itemsByType: Map<ResourceType, ListMultimap<String, ResourceItem>>) {
    for ((key, value) in itemsByType) {
      getOrCreateMap(key).putAll(value)
    }
  }

  override fun accept(visitor: ResourceVisitor): VisitResult {
    if (visitor.shouldVisitNamespace(namespace)) {
      if (acceptByResources(resourceTable, visitor) == ABORT) {
        return ABORT
      }
    }
    return CONTINUE
  }

  override fun getMap(
    namespace: ResourceNamespace,
    resourceType: ResourceType
  ): ListMultimap<String, ResourceItem>? =
    if (namespace != this.namespace) null else resourceTable[resourceType]

  private fun getOrCreateMap(type: ResourceType): ListMultimap<String, ResourceItem> =
    // Use LinkedListMultimap to preserve ordering for editors that show original order.
    resourceTable.computeIfAbsent(type) { LinkedListMultimap.create() }

  override fun getNamespace(): ResourceNamespace = namespace

  private fun checkResourceFilename(file: PathString): Boolean {
    val fileNameToResourceName = SdkUtils.fileNameToResourceName(file.fileName)
    return isIdentifier(fileNameToResourceName) && !isKeyword(fileNameToResourceName)
  }

  private class Loader(
    private val repository: ResourceFolderRepository
  ) : RepositoryLoader<ResourceFolderRepository>(
    resourceDirectoryOrFile = repository.resourceDir.toPath(),
    resourceFilesAndFolders = null,
    namespace = repository.namespace
  ) {
    private val resourceDir: File = repository.resourceDir
    private val resources = EnumMap<ResourceType, ListMultimap<String, ResourceItem>>(ResourceType::class.java)
    private val sources = mutableMapOf<File, ResourceFile>()
    private val fileResources = mutableMapOf<File, BasicFileResourceItem>()

    // The following two fields are used as a cache of size one for quick conversion from a PathString to a File.
    private var lastFile: File? = null
    private var lastPathString: PathString? = null

    fun load() {
      if (!resourceDirectoryOrFile.exists()) {
        return
      }

      scanResFolder()
      populateRepository()
    }

    private fun scanResFolder() {
      try {
        for (subDir in resourceDir.listFiles()!!.sorted()) {
          if (subDir.isDirectory) {
            val folderName = subDir.name
            val folderInfo = FolderInfo.create(folderName, folderConfigCache)
            if (folderInfo != null) {
              val configuration = getConfiguration(repository, folderInfo.configuration)
              for (file in subDir.listFiles()!!.sorted()) {
                if (file.name.startsWith(".")) {
                  continue // Skip file with the name starting with a dot.
                }
                if (
                  if (folderInfo.folderType == VALUES) {
                    sources.containsKey(file)
                  } else {
                    fileResources.containsKey(file)
                  }
                ) {
                  continue
                }
                val pathString = PathString(file)
                lastFile = file
                lastPathString = pathString
                loadResourceFile(pathString, folderInfo, configuration)
              }
            }
          }
        }
      } catch (e: Exception) {
        LOG.severe("Failed to load resources from $resourceDirectoryOrFile: $e")
      }

      super.finishLoading(repository)

      // Associate file resources with sources.
      for ((file, item) in fileResources.entries) {
        val source = sources.computeIfAbsent(file) { file ->
          ResourceFile(file, item.repositoryConfiguration)
        }
        source.addItem(item)
      }

      // Populate the resources map.
      val sortedSources = sources.values.toMutableList()
      // Sort sources according to folder configurations to have deterministic ordering of resource items in resources.
      sortedSources.sortWith(SOURCE_COMPARATOR)
      for (source in sortedSources) {
        for (item in source) {
          getOrCreateMap(item.type).put(item.name, item)
        }
      }
    }

    private fun loadResourceFile(
      file: PathString,
      folderInfo: FolderInfo,
      configuration: RepositoryConfiguration
    ) {
      if (folderInfo.resourceType == null) {
        if (isXmlFile(file)) {
          parseValueResourceFile(file, configuration)
        }
      } else if (repository.checkResourceFilename(file)) {
        if (isXmlFile(file) && folderInfo.isIdGenerating) {
          parseIdGeneratingResourceFile(file, configuration)
        }
        val item = createFileResourceItem(file, folderInfo.resourceType, configuration)
        addResourceItem(item, item.repository as ResourceFolderRepository)
      }
    }

    private fun populateRepository() {
      repository.commitToRepository(resources)
    }

    private fun getOrCreateMap(resourceType: ResourceType): ListMultimap<String, ResourceItem> =
      resources.computeIfAbsent(resourceType) { LinkedListMultimap.create<String, ResourceItem>() }

    private fun getFile(file: PathString): File? =
      if (file == lastPathString) lastFile else file.toFile()

    override fun addResourceItem(item: BasicResourceItem, repository: ResourceFolderRepository) {
      if (item is BasicValueResourceItemBase) {
        val sourceFile = item.sourceFile as ResourceFile
        val file = sourceFile.file
        if (file != null && !file.isDirectory) {
          sourceFile.addItem(item)
          sources[file] = sourceFile
        }
      } else if (item is BasicFileResourceItem) {
        val file = getFile(item.source)
        if (file != null && !file.isDirectory) {
          fileResources[file] = item
        }
      } else {
        throw IllegalArgumentException("Unexpected type: " + item.javaClass.name)
      }
    }

    override fun createResourceSourceFile(
      file: PathString,
      configuration: RepositoryConfiguration
    ): ResourceSourceFile = ResourceFile(getFile(file), configuration)
  }

  companion object {
    private val LOG: Logger = Logger.getLogger(ResourceFolderRepository::class.java.name)

    private val SOURCE_COMPARATOR =
      Comparator.comparing(ResourceFile::folderConfiguration)
  }
}
