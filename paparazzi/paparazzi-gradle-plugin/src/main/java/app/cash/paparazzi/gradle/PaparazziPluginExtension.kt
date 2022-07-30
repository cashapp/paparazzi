package app.cash.paparazzi.gradle

import java.io.File
import org.gradle.api.provider.Property

interface PaparazziPluginExtension {
  val snapshotRootDirectory: Property<File?>
}
