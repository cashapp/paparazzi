package app.cash.paparazzi.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class PaparazziPluginExtension
@Inject constructor(
  objectsFactory: ObjectFactory,
  layout: ProjectLayout
) {

  val snapshotDirectory: DirectoryProperty =
    objectsFactory.directoryProperty().convention(layout.projectDirectory.dir("src/test/snapshots"))
}
