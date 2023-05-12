package app.cash.paparazzi.internal.resources

import android.annotation.SuppressLint
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.ResourceVisitor.VisitResult
import com.android.ide.common.resources.ResourceVisitor.VisitResult.ABORT
import com.android.ide.common.resources.ResourceVisitor.VisitResult.CONTINUE
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.google.common.collect.ListMultimap
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumMap

/**
 * The [ResourceFolderRepository] is a leaf in the repository tree, and is used for user editable
 * resources (e.g. the resources in the project, typically the res/main source set.)
 *
 * Each [ResourceFolderRepository] contains the resources provided by a single res folder.
 */
@SuppressLint("NewApi")
class ResourceFolderRepository(
  private val resourceDir: File,
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

  override fun getNamespace(): ResourceNamespace = namespace
}
