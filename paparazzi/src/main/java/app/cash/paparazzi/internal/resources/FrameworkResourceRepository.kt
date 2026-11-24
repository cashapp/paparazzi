package app.cash.paparazzi.internal.resources

import android.annotation.SuppressLint
import app.cash.paparazzi.internal.resources.base.BasicResourceItem
import app.cash.paparazzi.internal.resources.base.BasicValueResourceItemBase
import com.android.SdkConstants.DOT_9PNG
import com.android.SdkConstants.FD_RES_RAW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.Resolver
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.utils.Base128InputStream
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.TreeSet
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Repository of resources of the Android framework. Most client code should use
 * the ResourceRepositoryManager.getFrameworkResources method to obtain framework resources.
 *
 * The repository can be loaded either from a res directory containing XML files, or from
 * framework_res.jar file, or from a binary cache file located under the directory returned by
 * the [PathManager.getSystemPath] method. This binary cache file can be created as
 * a side effect of loading the repository from a res directory.
 *
 * Loading from framework_res.jar or a binary cache file is 3-4 times faster than loading
 * from res directory.
 *
 * @see FrameworkResJarCreator
 */
internal class FrameworkResourceRepository private constructor(
  loader: RepositoryLoader<FrameworkResourceRepository>,
  private val useCompiled9Patches: Boolean
) : AarSourceResourceRepository(loader, null) {
  /**
   * Checks if the repository contains resources for the given set of languages.
   *
   * @param languages the set of ISO 639 language codes to check
   * @return true if the repository contains resources for all requested languages
   */
  fun containsLanguages(languages: Set<String>): Boolean {
    for (language: String in languages) {
      if (getLanguageGroup(language) !in this.languageGroups) {
        return false
      }
    }
    return true
  }

  /**
   * Loads resources for requested languages that are not present in this resource repository.
   *
   * @param languagesToLoad the set of ISO 639 language codes, or null to load all available languages
   * @return the new resource repository with additional resources, or this resource repository if it already contained
   * all requested languages
   */
  fun loadMissingLanguages(languagesToLoad: Set<String>?): FrameworkResourceRepository {
    val languageGroups = if (languagesToLoad == null) null else getLanguageGroups(languagesToLoad)
    if (languageGroups != null && this.languageGroups.containsAll(languageGroups)) {
      // The repository already contains all requested languages.
      return this
    }

    val loader = Loader(this, languageGroups)
    val newRepository = FrameworkResourceRepository(loader, useCompiled9Patches)
    newRepository.load(this, loader, languageGroups, loader.loadedLanguageGroups)
    return newRepository
  }

  private fun load(
    sourceRepository: FrameworkResourceRepository?,
    loader: Loader,
    languageGroups: Set<String>?,
    languageGroupsLoadedFromSourceRepositoryOrCache: Set<String>
  ) {
    val stringCache = Maps.newHashMapWithExpectedSize<String, String>(10000)
    val namespaceResolverCache = HashMap<NamespaceResolver, NamespaceResolver>()
    val configurationsToTakeOver = if (sourceRepository == null) {
      setOf()
    } else {
      copyFromRepository(sourceRepository, stringCache, namespaceResolverCache)
    }

    this.languageGroups += languageGroupsLoadedFromSourceRepositoryOrCache
    if (languageGroups == null ||
      !languageGroupsLoadedFromSourceRepositoryOrCache.containsAll(languageGroups)
    ) {
      loader.loadRepositoryContents(this)
    }

    populatePublicResourcesMap()
    freezeResources()
    takeOverConfigurations(configurationsToTakeOver)
  }

  override fun getPackageName(): String? = ANDROID_NAMESPACE.packageName

  override fun getResourceTypes(namespace: ResourceNamespace): Set<ResourceType> =
    if (namespace === ANDROID_NAMESPACE) Sets.immutableEnumSet(resources.keys) else setOf()

  /**
   * Copies resources from another FrameworkResourceRepository.
   *
   * @param sourceRepository the repository to copy resources from
   * @param stringCache the string cache to populate with the names of copied resources
   * @param namespaceResolverCache the namespace resolver cache to populate with namespace resolvers referenced by the copied resources
   * @return the [RepositoryConfiguration] objects referenced by the copied resources
   */
  private fun copyFromRepository(
    sourceRepository: FrameworkResourceRepository,
    stringCache: MutableMap<String, String>,
    namespaceResolverCache: MutableMap<NamespaceResolver, NamespaceResolver>
  ): Set<RepositoryConfiguration> {
    val resourceMaps = sourceRepository.resources.values

    // Copy resources from the source repository, get AarConfigurations that need to be taken over by this repository,
    // and pre-populate string and namespace resolver caches.
    val sourceConfigurations = Sets.newIdentityHashSet<RepositoryConfiguration>()
    for (resourceMap in resourceMaps) {
      for (item in resourceMap.values()) {
        addResourceItem(item)

        sourceConfigurations += (item as BasicResourceItem).repositoryConfiguration
        if (item is BasicValueResourceItemBase) {
          val resolver = item.namespaceResolver
          val namespaceResolver = if (resolver === Resolver.EMPTY_RESOLVER) {
            NamespaceResolver.EMPTY
          } else {
            resolver as NamespaceResolver
          }
          namespaceResolverCache[namespaceResolver] = namespaceResolver
        }
        val name = item.name
        stringCache[name] = name
      }
    }

    return sourceConfigurations
  }

  val languageGroups: MutableSet<String>
    get() {
      val languages = TreeSet<String>()
      for (resourceMap in resources.values) {
        for (item in resourceMap.values()) {
          languages += getLanguageGroup(item.configuration)
        }
      }
      return languages
    }

  private fun updateResourcePath(relativeResourcePath: String): String =
    if (useCompiled9Patches && relativeResourcePath.endsWith(DOT_9PNG)) {
      val beginning =
        relativeResourcePath.substring(0, relativeResourcePath.length - DOT_9PNG.length)
      val ending = relativeResourcePath.substring(relativeResourcePath.length)
      "$beginning$COMPILED_9PNG_EXTENSION$ending"
    } else {
      relativeResourcePath
    }

  override fun getResourceUrl(relativeResourcePath: String): String =
    super.getResourceUrl(updateResourcePath(relativeResourcePath))

  override fun getSourceFile(relativeResourcePath: String, forFileResource: Boolean): PathString =
    super.getSourceFile(updateResourcePath(relativeResourcePath), forFileResource)

  private class Loader : RepositoryLoader<FrameworkResourceRepository> {
    val publicXmlFileNames = listOf("public.xml", "public-final.xml", "public-staging.xml")

    val loadedLanguageGroups: Set<String>

    private var languageGroups: Set<String>?

    constructor(
      resourceDirectoryOrFile: Path,
      languageGroups: Set<String>?
    ) : super(resourceDirectoryOrFile, null, ANDROID_NAMESPACE) {
      this.languageGroups = languageGroups
      loadedLanguageGroups = TreeSet()
    }

    constructor(
      sourceRepository: FrameworkResourceRepository,
      languageGroups: Set<String>?
    ) : super(sourceRepository.resourceDirectoryOrFile, null, ANDROID_NAMESPACE) {
      this.languageGroups = languageGroups
      loadedLanguageGroups = TreeSet(sourceRepository.languageGroups)
    }

    @SuppressLint("NewApi")
    override fun loadFromZip(repository: FrameworkResourceRepository) {
      try {
        ZipFile(resourceDirectoryOrFile.toFile()).use { zipFile ->
          if (languageGroups == null) {
            languageGroups = readLanguageGroups(zipFile)
          }

          val stringCache = Maps.newHashMapWithExpectedSize<String, String>(10000)
          val namespaceResolverCache = HashMap<NamespaceResolver, NamespaceResolver>()

          for (language in languageGroups!!) {
            if (!loadedLanguageGroups.contains(language)) {
              val entryName = getResourceTableNameForLanguage(language)
              val zipEntry = zipFile.getEntry(entryName)
                ?: if (language.isEmpty()) {
                  throw IOException("\"$entryName\" not found in $resourceDirectoryOrFile")
                } else {
                  continue // Requested language may not be represented in the Android framework resources.
                }
              Base128InputStream(zipFile.getInputStream(zipEntry)).use { stream ->
                repository.loadFromStream(stream, stringCache, namespaceResolverCache)
              }
            }
          }

          repository.populatePublicResourcesMap()
          repository.freezeResources()
        }
      } catch (e: Exception) {
        LOG.severe("Failed to load resources from $resourceDirectoryOrFile: $e")
      }
    }

    override fun loadRepositoryContents(repository: FrameworkResourceRepository) {
      super.loadRepositoryContents(repository)
      val languageGroups = languageGroups ?: repository.languageGroups
      repository.languageGroups += languageGroups
    }

    @SuppressLint("NewApi")
    override fun isIgnored(fileOrDirectory: Path, attrs: BasicFileAttributes): Boolean {
      if (fileOrDirectory == resourceDirectoryOrFile) {
        return false
      }

      if (super.isIgnored(fileOrDirectory, attrs)) {
        return true
      }

      val fileName: String = fileOrDirectory.fileName.toString()
      if (attrs.isDirectory) {
        if (fileName.startsWith("values-mcc") ||
          (
            fileName.startsWith(FD_RES_RAW) &&
              (fileName.length == FD_RES_RAW.length || fileName[FD_RES_RAW.length] == '-')
            )
        ) {
          // Mobile country codes and raw resources are not used by LayoutLib.
          return true
        }

        // Skip folders that don't belong to languages in languageGroups or languages that were loaded earlier.
        if (languageGroups != null || loadedLanguageGroups.isNotEmpty()) {
          val config = FolderConfiguration.getConfigForFolder(fileName) ?: return true
          val language = getLanguageGroup(config)
          if ((languageGroups != null && !languageGroups!!.contains(language)) ||
            loadedLanguageGroups.contains(language)
          ) {
            return true
          }
          folderConfigCache[config.qualifierString] = config
        }
      } else if ((publicXmlFileNames.contains(fileName) || (fileName == "symbols.xml")) &&
        "values" == PathString(fileOrDirectory).parentFileName
      ) {
        // Skip files that don't contain resources.
        return true
      } else if (fileName.endsWith(COMPILED_9PNG_EXTENSION)) {
        return true
      }

      return false
    }

    override fun addResourceItem(item: BasicResourceItem, repository: FrameworkResourceRepository) =
      repository.addResourceItem(item)

    override fun getKeyForVisibilityLookup(resourceName: String): String {
      // This class obtains names of public resources from public.xml where all resource names are preserved
      // in their original form. This is different from the superclass that obtains the names from public.txt
      // where the names are transformed by replacing dots, colons and dashes with underscores.
      return resourceName
    }

    companion object {
      @SuppressLint("NewApi")
      private fun readLanguageGroups(zipFile: ZipFile): Set<String> {
        val result = sortedSetOf<String>(comparator = Comparator.naturalOrder())
        result += ""
        zipFile.stream().forEach { entry: ZipEntry ->
          val name = entry.name
          if (name.startsWith(RESOURCES_TABLE_PREFIX) &&
            name.endsWith(RESOURCE_TABLE_SUFFIX) &&
            name.length == RESOURCES_TABLE_PREFIX.length + RESOURCE_TABLE_SUFFIX.length + 2 &&
            Character.isLetter(name[RESOURCES_TABLE_PREFIX.length]) &&
            Character.isLetter(name[RESOURCES_TABLE_PREFIX.length + 1])
          ) {
            result += name.substring(
              startIndex = RESOURCES_TABLE_PREFIX.length,
              endIndex = RESOURCES_TABLE_PREFIX.length + 2
            )
          }
        }
        return result.toSet()
      }
    }
  }

  /**
   * Redirects the [RepositoryConfiguration] inherited from another repository to point to this one, so that
   * the other repository can be garbage collected. This has to be done after this repository is fully loaded.
   *
   * @param sourceConfigurations the configurations to reparent
   */
  private fun takeOverConfigurations(sourceConfigurations: Set<RepositoryConfiguration>) {
    for (configuration in sourceConfigurations) {
      configuration.transferOwnershipTo(this)
    }
  }

  companion object {
    private val ANDROID_NAMESPACE = ResourceNamespace.ANDROID

    /** Mapping from languages to language groups, e.g. Romansh is mapped to Italian.  */
    private val LANGUAGE_TO_GROUP = mapOf("rm" to "it")
    private const val RESOURCES_TABLE_PREFIX = "resources_"
    private const val RESOURCE_TABLE_SUFFIX = ".bin"
    private const val COMPILED_9PNG_EXTENSION = ".compiled.9.png"
    private val LOG = Logger.getLogger(FrameworkResourceRepository::class.java.name)

    /**
     * Creates an Android framework resource repository.
     *
     * @param resourceDirectoryOrFile the res directory or a jar file containing resources of the Android framework
     * @param languagesToLoad the set of ISO 639 language codes, or null to load all available languages
     * @param useCompiled9Patches whether to provide the compiled or non-compiled version of the framework 9-patches
     * @return the created resource repository
     */
    fun create(
      resourceDirectoryOrFile: Path,
      languagesToLoad: Set<String>?,
      useCompiled9Patches: Boolean
    ): FrameworkResourceRepository {
      val languageGroups = if (languagesToLoad == null) null else getLanguageGroups(languagesToLoad)

      val loader = Loader(resourceDirectoryOrFile, languageGroups)
      val repository = FrameworkResourceRepository(loader, useCompiled9Patches)

      repository.load(null, loader, languageGroups, loader.loadedLanguageGroups)
      return repository
    }

    /**
     * Returns the name of the resource table file containing resources for the given language.
     *
     * @param language the two-letter language abbreviation, or an empty string for language-neutral resources
     * @return the file name
     */
    fun getResourceTableNameForLanguage(language: String): String =
      if (language.isEmpty()) {
        "resources.bin"
      } else {
        "$RESOURCES_TABLE_PREFIX$language$RESOURCE_TABLE_SUFFIX"
      }

    fun getLanguageGroup(config: FolderConfiguration): String {
      val locale = config.localeQualifier
      return if (locale == null) "" else getLanguageGroup(locale.language ?: "")
    }

    /**
     * Maps some languages to others effectively grouping languages together. For example, Romansh language
     * that has very few framework resources is grouped together with Italian.
     *
     * @param language the original language
     * @return the language representing the corresponding group of languages
     */
    private fun getLanguageGroup(language: String): String = LANGUAGE_TO_GROUP.getOrDefault(language, language)

    private fun getLanguageGroups(languages: Set<String>): Set<String> {
      val result = TreeSet<String>()
      result += ""
      for (language: String in languages) {
        result += getLanguageGroup(language)
      }
      return result
    }
  }
}
