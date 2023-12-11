package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttributeFormat.ENUM
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.resources.ResourceType.ATTR
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceVisibility
import com.android.resources.ResourceVisibility.PRIVATE
import com.android.resources.ResourceVisibility.PUBLIC
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class FrameworkResourceRepositoryTest {
  @Test
  fun fullLoadingFromJar() {
    for (languages in listOf(setOf(), setOf("fr", "de"), null)) {
      val fromJar = FrameworkResourceRepository.create(
        resourceDirectoryOrFile = getFrameworkResJar(),
        languagesToLoad = languages,
        useCompiled9Patches = false
      )

      checkLanguages(fromJar, languages)
      checkContents(fromJar)
    }
  }

  @Test
  fun incrementalLoadingFromJar() {
    val withFrench = FrameworkResourceRepository.create(
      resourceDirectoryOrFile = getFrameworkResJar(),
      languagesToLoad = setOf("fr"),
      useCompiled9Patches = false
    )
    checkLanguages(withFrench, setOf("fr"))
    checkContents(withFrench)

    val withFrenchAndGerman = withFrench.loadMissingLanguages(setOf("de"))
    checkLanguages(withFrenchAndGerman, setOf("fr", "de"))
    checkContents(withFrenchAndGerman)
  }

  @Test
  fun useCompiled9Patches() {
    val repository = FrameworkResourceRepository.create(
      resourceDirectoryOrFile = getFrameworkResJar(),
      languagesToLoad = emptySet(),
      useCompiled9Patches = true
    )

    val resourceUrl = repository.getResourceUrl("drawable-hdpi/textfield_search_activated_mtrl_alpha.9.png")
    assertThat(resourceUrl).isEqualTo("jar://src/test/resources/framework/framework_res.jar!/res/drawable-hdpi/textfield_search_activated_mtrl_alpha.compiled.9.png")
  }

  @Test
  fun notUseCompiled9Patches() {
    val repository = FrameworkResourceRepository.create(
      resourceDirectoryOrFile = getFrameworkResJar(),
      languagesToLoad = emptySet(),
      useCompiled9Patches = false
    )

    val resourceUrl = repository.getResourceUrl("drawable-hdpi/textfield_search_activated_mtrl_alpha.9.png")
    assertThat(resourceUrl).isEqualTo("jar://src/test/resources/framework/framework_res.jar!/res/drawable-hdpi/textfield_search_activated_mtrl_alpha.9.png")
  }

  private fun getFrameworkResJar(): Path =
    Paths.get("src/test/resources/framework/framework_res.jar")

  companion object {
    private fun checkLanguages(
      repository: FrameworkResourceRepository,
      languages: Set<String>?
    ) {
      if (languages == null) {
        assertThat(repository.languageGroups.size).isAtLeast(75)
      } else {
        assertThat(repository.languageGroups).isEqualTo(languages.union(setOf("")))
      }
    }
  }

  private fun checkContents(repository: ResourceRepository) {
    checkPublicResources(repository)
    checkAttributes(repository)
    checkIdResources(repository)
  }

  private fun checkPublicResources(repository: ResourceRepository) {
    val resourceItems = repository.allResources
    assertWithMessage("Too few resources: ${resourceItems.size}").that(resourceItems.size)
      .isAtLeast(10000)
    for (item in resourceItems) {
      assertThat(item.namespace).isEqualTo(ResourceNamespace.ANDROID)
    }
    val expectations = mapOf(
      ResourceType.STYLE to 700,
      ResourceType.ATTR to 1200,
      ResourceType.DRAWABLE to 600,
      ResourceType.ID to 60,
      ResourceType.LAYOUT to 20
    )
    for (type in ResourceType.values()) {
      val publicItems = repository.getPublicResources(ResourceNamespace.ANDROID, type)
      val minExpected = expectations[type]
      if (minExpected != null) {
        assertWithMessage("Too few public resources of type " + type.getName()).that(publicItems.size)
          .isAtLeast(minExpected)
      }
    }

    // Not mentioned in public.xml.
    assertVisibility(repository, ResourceType.STRING, "byteShort", PRIVATE)
    // Defined at top level.
    assertVisibility(repository, ResourceType.STYLE, "Widget.DeviceDefault.Button.Colored", PUBLIC)
    // Defined inside a <public-group>.
    assertVisibility(repository, ResourceType.ATTR, "packageType", PUBLIC)
    // Due to the @hide comment
    assertVisibility(repository, ResourceType.DRAWABLE, "ic_info", PRIVATE)
    // Due to the naming convention
    assertVisibility(repository, ResourceType.ATTR, "__removed2", PRIVATE)
  }

  private fun assertVisibility(
    repository: ResourceRepository,
    type: ResourceType,
    name: String,
    visibility: ResourceVisibility
  ) {
    val resources = repository.getResources(ResourceNamespace.ANDROID, type, name)
    assertThat(resources).isNotEmpty()
    assertThat((resources[0] as ResourceItemWithVisibility).visibility).isEqualTo(visibility)
  }

  private fun checkAttributes(repository: ResourceRepository) {
    // `typeface` is declared first at top-level and later referenced from within `<declare-styleable>`.
    // Make sure the later reference doesn't shadow the original definition.
    var attrValue = getAttrValue(repository, "typeface")
    assertThat(attrValue).isNotNull()
    assertThat(attrValue.formats).containsExactly(ENUM)
    assertThat(attrValue.description).isEqualTo("Default text typeface.")
    assertThat(attrValue.groupName).isEqualTo("Other non-theme attributes")
    var valueMap = attrValue.attributeValues
    assertThat(valueMap.size).isEqualTo(4)
    assertThat(valueMap).containsEntry("monospace", 3)
    assertThat(attrValue.getValueDescription("monospace")).isNull()

    // `appCategory` is defined only in attr_manifest.xml.
    attrValue = getAttrValue(repository, "appCategory")
    assertThat(attrValue).isNotNull()
    assertThat(attrValue.formats).containsExactly(ENUM)
    assertThat(attrValue.description).startsWith("Declare the category of this app")
    assertThat(attrValue.groupName).isNull()
    valueMap = attrValue.attributeValues
    assertThat(valueMap.size).isAtLeast(7)
    assertThat(valueMap).containsEntry("maps", 6)
    assertThat(attrValue.getValueDescription("maps")).contains("navigation")
  }

  private fun getAttrValue(
    repository: ResourceRepository,
    attrName: String
  ): AttrResourceValue {
    val attrItem = repository.getResources(ResourceNamespace.ANDROID, ATTR, attrName)[0]
    return attrItem.resourceValue as AttrResourceValue
  }

  private fun checkIdResources(repository: ResourceRepository) {
    var items = repository.getResources(ResourceNamespace.ANDROID, ID, "mode_normal")
    items = items.filter { it.configuration.isDefault }
    assertThat(items).hasSize(1)

    // Check that ID resources defined using @+id syntax in layout XML files are present in the repository.
    // The following ID resource is defined by android:id="@+id/radio_power" in layout/power_dialog.xml.
    items = repository.getResources(ResourceNamespace.ANDROID, ID, "radio_power")
    assertThat(items).hasSize(1)
  }
}
