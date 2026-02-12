package app.cash.paparazzi

import java.io.File

internal const val ARTIFACTS_DIRECTORY_NAME = "artifacts"

internal fun Snapshot.artifactFile(artifactName: String, artifactsDirectory: File): File {
  val (artifactDirectoryName, artifactExtension) = artifactName.toArtifactPathParts()
  return File(
    File(artifactsDirectory, artifactDirectoryName),
    toFileName("_", artifactExtension)
  )
}

private fun String.toArtifactPathParts(): Pair<String, String> {
  val rawExtension = substringAfterLast('.', "")
  val extension = rawExtension.sanitizeForFilename().ifBlank { "txt" }
  val rawDirectoryName = if (rawExtension.isBlank()) this else substringBeforeLast('.')
  val directoryName = rawDirectoryName.sanitizeForFilename().ifBlank { "artifact" }
  return directoryName to extension
}
