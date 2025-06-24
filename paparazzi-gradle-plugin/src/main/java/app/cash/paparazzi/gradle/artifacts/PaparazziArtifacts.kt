package app.cash.paparazzi.gradle.artifacts

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.model.ObjectFactory

internal interface PaparazziArtifacts : Named {
  companion object {
    @JvmField val PAPARAZZI_ARTIFACTS_ATTRIBUTE: Attribute<PaparazziArtifacts> =
      Attribute.of("paparazzi.internal.artifacts", PaparazziArtifacts::class.java)

    @JvmField val CATEGORY_ATTRIBUTE: Attribute<Category> = Category.CATEGORY_ATTRIBUTE

    fun category(objects: ObjectFactory): Category {
      return objects.named(Category::class.java, "snapshot")
    }
  }

  enum class Kind(
    val declarableName: String,
    val artifactName: String
  ) {
    SNAPSHOT_METADATA("snapshotMetadata", "snapshot-metadata")
  }
}
