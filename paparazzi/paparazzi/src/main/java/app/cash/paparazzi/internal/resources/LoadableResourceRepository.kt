package app.cash.paparazzi.internal.resources

import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.ide.common.util.PathString
import java.nio.file.Path

/**
 * Repository of resources loaded from a file or a directory on disk.
 */
interface LoadableResourceRepository : SingleNamespaceResourceRepository {
  /**
   * Returns the name of the library, or null if this is not an AAR resource repository.
   */
  val libraryName: String?

  /**
   * Returns the name of this resource repository to display in the UI.
   */
  val displayName: String

  /**
   * Returns the file or directory this resource repository was loaded from. Resource repositories loaded from
   * the same file or directory with different file filtering options have the same origin.
   */
  val origin: Path

  /**
   * Produces a string to be returned by the [BasicResourceItem.getValue] method.
   * The string represents an URL in one of the following formats:
   *
   *  * file URL, e.g. "file:///foo/bar/res/layout/my_layout.xml"
   *  * URL of a zipped element inside the res.apk file, e.g. "apk:///foo/bar/res.apk!/res/layout/my_layout.xml"
   *
   *
   * @param relativeResourcePath the relative path of a file resource
   * @return the URL pointing to the file resource
   */
  fun getResourceUrl(relativeResourcePath: String): String

  /**
   * Produces a [PathString] to be returned by the [BasicResourceItem.getSource] method.
   *
   * @param relativeResourcePath the relative path of the file the resource was created from
   * @param forFileResource true is the resource is a file resource, false if it is a value resource
   * @return the PathString to be returned by the [BasicResourceItem.getSource] method
   */
  fun getSourceFile(relativeResourcePath: String, forFileResource: Boolean): PathString

  /**
   * Produces a [PathString] to be returned by the [BasicResourceItem.getOriginalSource] method.
   *
   * @param relativeResourcePath the relative path of the file the resource was created from
   * @param forFileResource true is the resource is a file resource, false if it is a value resource
   * @return the PathString to be returned by the [BasicResourceItem.getOriginalSource] method
   */
  fun getOriginalSourceFile(
    relativeResourcePath: String,
    forFileResource: Boolean
  ): PathString? {
    return getSourceFile(relativeResourcePath, forFileResource)
  }

  fun containsUserDefinedResources(): Boolean
}
