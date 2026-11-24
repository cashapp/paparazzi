package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AarSourceResourceRepositoryTest {
  @get:Rule
  val tempDir: TemporaryFolder = TemporaryFolder()

  @Test
  fun getAllDeclaredIds_hasRDotTxt() {
    // R.txt contains these 3 ids which are actually not defined anywhere else.
    // The layout file contains "id_from_layout" but it should not be parsed if R.txt is present.
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib")
    assertThat(
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID).keySet()
    ).containsExactly("id1", "id2", "id3")
  }

  @Test fun getAllDeclaredIds_noRDotTxt() {
    // There's no R.txt, so the layout file should be parsed and the two ids found.
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib_noRDotTxt")
    assertThat(
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID).keySet()
    ).containsExactly(
      "btn_title_refresh",
      "bug123032845",
      "header",
      "image",
      "imageButton",
      "imageView",
      "imageView2",
      "nonExistent",
      "noteArea",
      "styledView",
      "text2",
      "title_refresh_progress"
    )
  }

  @Test
  fun getAllDeclaredIds_wrongRDotTxt() {
    // IDs should come from R.txt, not parsing the layout.
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib_wrongRDotTxt")
    assertThat(
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID).keySet()
    ).containsExactly("id1", "id2", "id3")
  }

  @Test fun getAllDeclaredIds_brokenRDotTxt() {
    // We can't parse R.txt, so we fall back to parsing layouts.
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib_brokenRDotTxt")
    assertThat(
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ID).keySet()
    ).containsExactly("id_from_layout")
  }

  @Test fun getAllDeclaredIds_unrecognizedTag() {
    val repository = makeAarRepositoryFromExplodedAar("unrecognizedTag")
    assertThat(
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.COLOR).keySet()
    ).containsExactly("black", "white")
  }

  @Test fun multipleValues_wholeResourceDirectory_exploded() {
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib")
    checkRepositoryContents(repository)
  }

  @Test fun multipleValues_wholeResourceDirectory_unexploded() {
    val repository = makeAarRepositoryFromAarArtifact(tempDir.root.toPath(), "my_aar_lib")
    checkRepositoryContents(repository)
  }

  @Test fun multipleValues_partOfResourceDirectories() {
    val repository = makeAarRepositoryFromExplodedAarFiltered(
      "my_aar_lib",
      "values/strings.xml",
      "values-fr/strings.xml"
    )
    val items = repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, "hello")
    val helloVariants = getValues(items)
    assertThat(helloVariants).containsExactly("bonjour", "hello")
  }

  @Test fun libraryNameIsMaintained() {
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib")
    assertThat(repository.libraryName).isEqualTo(AAR_LIBRARY_NAME)
    for (item in repository.allResources) {
      assertThat(item.libraryName).isEqualTo(AAR_LIBRARY_NAME)
    }
  }

  @Test fun packageName() {
    val repository = makeAarRepositoryFromExplodedAar("my_aar_lib")
    assertThat(repository.packageName).isEqualTo(AAR_PACKAGE_NAME)
  }

  companion object {
    private fun getValues(items: List<ResourceItem>): List<String> =
      buildList {
        items.forEach { item ->
          val resourceValue = item.resourceValue
          assertThat(resourceValue).isNotNull()
          this += resourceValue.value
        }
      }

    private fun checkRepositoryContents(repository: AarSourceResourceRepository) {
      var items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.STRING,
        resourceName = "hello"
      )
      val helloVariants = getValues(items)
      assertThat(helloVariants).containsExactly("bonjour", "hello", "hola")

      items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.STYLE,
        resourceName = "MyTheme.Dark"
      )
      assertThat(items.size).isEqualTo(1)
      val styleValue = items[0].resourceValue as StyleResourceValue
      assertThat(styleValue.parentStyleName).isEqualTo("android:Theme.Light")
      val styleItems = styleValue.definedItems
      assertThat(styleItems.size).isEqualTo(2)
      val textColor = styleValue.getItem(ResourceNamespace.ANDROID, "textColor")
      assertThat(textColor.attrName).isEqualTo("android:textColor")
      assertThat(textColor.value).isEqualTo("#999999")
      val foo = styleValue.getItem(ResourceNamespace.RES_AUTO, "foo")
      assertThat(foo.attrName).isEqualTo("foo")
      assertThat(foo.value).isEqualTo("?android:colorForeground")

      items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.STYLEABLE,
        resourceName = "Styleable1"
      )
      assertThat(items.size).isEqualTo(1)
      var styleableValue = items[0].resourceValue as StyleableResourceValue
      var attributes = styleableValue.allAttributes
      assertThat(attributes.size).isEqualTo(1)
      var attr = attributes[0]
      assertThat(attr.name).isEqualTo("some_attr")
      assertThat(attr.formats).containsExactly(AttributeFormat.COLOR)

      items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.STYLEABLE,
        resourceName = "Styleable.with.dots"
      )
      assertThat(items.size).isEqualTo(1)
      styleableValue = items[0].resourceValue as StyleableResourceValue
      attributes = styleableValue.allAttributes
      assertThat(attributes.size).isEqualTo(1)
      attr = attributes[0]
      assertThat(attr.name).isEqualTo("some_attr")
      assertThat(attr.formats).containsExactly(AttributeFormat.COLOR)

      items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.ATTR,
        resourceName = "some_attr"
      )
      assertThat(items.size).isEqualTo(1)
      attr = items[0].resourceValue as AttrResourceValue
      assertThat(attr.name).isEqualTo("some_attr")
      assertThat(attr.formats).containsExactly(AttributeFormat.COLOR)

      items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.ATTR,
        resourceName = "app_attr1"
      )
      assertThat(items).isEmpty()

      items = repository.getResources(
        namespace = ResourceNamespace.RES_AUTO,
        resourceType = ResourceType.ATTR,
        resourceName = "app_attr2"
      )
      assertThat(items.size).isEqualTo(1)
      attr = items[0].resourceValue as AttrResourceValue
      assertThat(attr.name).isEqualTo("app_attr2")
      assertThat(attr.formats).containsExactly(
        AttributeFormat.BOOLEAN,
        AttributeFormat.COLOR,
        AttributeFormat.DIMENSION,
        AttributeFormat.FLOAT,
        AttributeFormat.FRACTION,
        AttributeFormat.INTEGER,
        AttributeFormat.REFERENCE,
        AttributeFormat.STRING
      )
    }
  }
}
