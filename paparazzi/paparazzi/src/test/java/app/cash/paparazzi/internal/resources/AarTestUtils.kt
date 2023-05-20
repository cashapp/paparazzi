@file:OptIn(ExperimentalPathApi::class)

package app.cash.paparazzi.internal.resources

import com.android.SdkConstants.DOT_AAR
import com.android.ide.common.util.toPathString
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.visitFileTree

internal const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0"
internal const val AAR_PACKAGE_NAME = "com.test.testlibrary"
internal const val TEST_DATA_DIR = "src/test/resources/aar"

/**
 * Representative of loading an unzipped AAR from the Gradle transforms cache, e.g.,
 * $GRADLE_USER_HOME/caches/transforms-3/93260cec846aa69823e5e1c7eb771238/transformed/appcompat-1.6.1/res
 */
internal fun makeAarRepositoryFromExplodedAar(
  libraryDirName: String
): AarSourceResourceRepository {
  return AarSourceResourceRepository.create(
    resourceDirectoryOrFile = resolveProjectPath("$TEST_DATA_DIR/$libraryDirName/res"),
    libraryName = AAR_LIBRARY_NAME
  )
}

/**
 * Same as [makeAarRepositoryFromExplodedAar], but only selecting certain resource folders
 */
internal fun makeAarRepositoryFromExplodedAarFiltered(
  libraryDirName: String,
  vararg resources: String
): AarSourceResourceRepository {
  val root = resolveProjectPath("$TEST_DATA_DIR/$libraryDirName/res").toPathString()
  return AarSourceResourceRepository.create(
    resourceFolderRoot = root,
    resourceFolderResources = resources.map { resource -> root.resolve(resource) },
    libraryName = AAR_LIBRARY_NAME
  )
}

/**
 * Representative of loading a downloaded AAR from the Gradle cache, e.g.,
 * $GRADLE_USER_HOME/caches/modules-2/files-2.1/androidx.appcompat/appcompat/1.6.1/6c7577004b7ebbee5ed87d512b578dd20e3c8c31/appcompat-1.6.1.aar
 *
 * Given a [libraryDirName] pointing to an exploded aar root, create an aar "on-the-fly" with parent [tempDir].
 */
internal fun makeAarRepositoryFromAarArtifact(
  tempDir: Path,
  libraryDirName: String
): AarSourceResourceRepository {
  val sourceDirectory = resolveProjectPath("$TEST_DATA_DIR/$libraryDirName")
  return AarSourceResourceRepository.create(
    resourceDirectoryOrFile = createAar(tempDir, sourceDirectory),
    libraryName = AAR_LIBRARY_NAME
  )
}

private fun createAar(tempDir: Path, sourceDirectory: Path): Path {
  return tempDir.resolve("${sourceDirectory.fileName}$DOT_AAR").also { aarPath ->
    ZipOutputStream(aarPath.outputStream().buffered()).use { zip ->
      sourceDirectory.visitFileTree {
        onVisitFile { file, _ ->
          val relativePath = sourceDirectory.relativize(file).invariantSeparatorsPathString
          val entry = ZipEntry(relativePath)
          zip.putNextEntry(entry)
          zip.write(file.readBytes())
          zip.closeEntry()
          return@onVisitFile CONTINUE
        }
      }
    }
  }
}

/**
 * Returns the absolute path, given a file or directory relative to the base of the current project,
 *
 * @throws IllegalArgumentException if the path results in a file that is not found.
 */
internal fun resolveProjectPath(relativePath: String): Path {
  val f = projectRoot.resolve(relativePath)
  if (f.exists()) {
    return f
  }

  throw IllegalArgumentException("File \"$relativePath\" not found at \"$projectRoot\"")
}

private var projectRoot = Paths.get("").toAbsolutePath()
