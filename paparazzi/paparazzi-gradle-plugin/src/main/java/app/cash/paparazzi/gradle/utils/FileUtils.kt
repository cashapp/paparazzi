package app.cash.paparazzi.gradle.utils

import com.android.ide.common.util.toPathString
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import java.io.File

fun ConfigurableFileCollection.joinFiles(directory: Directory) = files.joinToString(",") { file ->
  directory.relativize(file)
}

fun Directory.relativize(child: File): String {
  println("Directory path: " + asFile.toPathString())
  println("child path: " + child.toPathString())
  println("relative path: " + asFile.toPath().relativize(child.toPath()).toFile().toPathString())
  println("invariantSeparatorsPath: " + asFile.toPath().relativize(child.toPath()).toFile().invariantSeparatorsPath)

  return asFile.toPath().relativize(child.toPath()).toFile().invariantSeparatorsPath
}
