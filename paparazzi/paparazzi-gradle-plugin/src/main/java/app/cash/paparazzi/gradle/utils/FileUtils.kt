package app.cash.paparazzi.gradle.utils

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import java.io.File

fun ConfigurableFileCollection.joinFiles(directory: Directory) = files.joinToString(",") { file ->
  directory.relativize(file)
}

fun Directory.relativize(child: File): String {
  return asFile.toPath().relativize(child.toPath()).toFile().invariantSeparatorsPath
}
