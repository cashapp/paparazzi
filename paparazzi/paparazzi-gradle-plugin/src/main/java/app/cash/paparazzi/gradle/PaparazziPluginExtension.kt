package app.cash.paparazzi.gradle

import org.gradle.api.provider.Property
import java.io.File

interface PaparazziPluginExtension {
  val snapshotRootDirectory: Property<File?>
}
