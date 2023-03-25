package app.cash.paparazzi.gradle

import org.gradle.api.file.DirectoryProperty

interface PaparazziPluginExtension {

  val snapshotDirectory: DirectoryProperty
}
