package app.cash.paparazzi.internal

import com.android.SdkConstants.FD_RES
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.MergingException
import com.android.ide.common.resources.ResourceMerger
import com.android.ide.common.resources.ResourceSet
import com.android.ide.common.resources.TestResourceRepository
import com.android.utils.ILogger
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.logging.Logger

/**
 * Fixture for creating resource repositories for tests.
 */
class ResourceRepositoryFixture : ExternalResource() {
  private val tmpFolder = TemporaryFolder.builder().assureDeletion().build()

  override fun before() {
    tmpFolder.create()
  }

  override fun after() {
    tmpFolder.delete()
  }

  /**
   * Creates a [TestResourceRepository] for a resource folder whose contents is identified
   * by the pairs of relative paths and file contents
   */
  @Throws(IOException::class)
  fun createTestResources(
    namespace: ResourceNamespace,
    pathToContents: Map<String, String>
  ): TestResourceRepository {
    val dir = tmpFolder.newFolder()
    val res = File(dir, FD_RES)
    res.mkdirs()

    pathToContents.forEach { (relativePath, fileContents) ->
      val platformRelativePath = relativePath.replace('/', File.separatorChar)
      val file = File(res, platformRelativePath)
      val parent = file.parentFile
      parent.mkdirs()
      FileSystem.SYSTEM.write(file.toOkioPath()) { writeUtf8(fileContents) }
    }

    val resFolder = File(dir, FD_RES)

    val merger = ResourceMerger(0)
    val resourceSet = ResourceSet("main", namespace, null, false, null)
    resourceSet.addSource(resFolder)
    resourceSet.setTrackSourcePositions(false)
    try {
      resourceSet.loadFromFiles(RecordingLogger())
    } catch (e: MergingException) {
      LOG.warning(e.message)
    }
    merger.addDataSet(resourceSet)

    val repository = TestResourceRepository(namespace)
    repository.update(merger)

    return repository
  }

  companion object {
    private val LOG = Logger.getLogger(ResourceRepositoryFixture::class.java.name)
  }

  class RecordingLogger : ILogger {
    override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) = Unit
    override fun warning(msgFormat: String, vararg args: Any?) = Unit
    override fun info(msgFormat: String, vararg args: Any?) = Unit
    override fun verbose(msgFormat: String, vararg args: Any?) = Unit
  }
}
