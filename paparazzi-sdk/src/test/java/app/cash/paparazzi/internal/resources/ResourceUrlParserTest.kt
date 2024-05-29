package app.cash.paparazzi.internal.resources

import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResourceUrlParserTest {
  @Test
  fun test() {
    val parser = ResourceUrlParser()

    parser.assertResourceUrl("@id/foo")
      .hasAttributes(
        namespace = null,
        resourceType = ResourceType.ID,
        resourceName = "foo",
        qualifiedResourceName = "foo"
      )

    parser.assertResourceUrl("@+id/foo")
      .hasAttributes(
        namespace = null,
        resourceType = ResourceType.ID,
        resourceName = "foo",
        qualifiedResourceName = "foo"
      )

    parser.assertResourceUrl("@layout/foo")
      .hasAttributes(
        namespace = null,
        resourceType = ResourceType.LAYOUT,
        resourceName = "foo",
        qualifiedResourceName = "foo"
      )

    parser.assertResourceUrl("@dimen/foo")
      .hasAttributes(
        namespace = null,
        resourceType = ResourceType.DIMEN,
        resourceName = "foo",
        qualifiedResourceName = "foo"
      )

    parser.assertResourceUrl("@android:dimen/foo")
      .hasAttributes(
        namespace = "android",
        resourceType = ResourceType.DIMEN,
        resourceName = "foo",
        qualifiedResourceName = "android:foo"
      )

    parser.assertResourceUrl("?attr/foo")
      .hasAttributes(
        namespace = null,
        resourceType = ResourceType.ATTR,
        resourceName = "foo",
        qualifiedResourceName = "foo"
      )

    parser.assertResourceUrl("?foo")
      .hasAttributes(
        namespace = null,
        resourceType = null,
        resourceName = "foo",
        qualifiedResourceName = "foo"
      )

    parser.assertResourceUrl("?android:foo")
      .hasAttributes(
        namespace = "android",
        resourceType = null,
        resourceName = "foo",
        qualifiedResourceName = "android:foo"
      )

    parser.assertResourceUrl("?androidx:foo")
      .hasAttributes(
        namespace = "androidx",
        resourceType = null,
        resourceName = "foo",
        qualifiedResourceName = "androidx:foo"
      )

    parser.assertResourceUrl("@my_package:layout/my_name")
      .hasAttributes(
        namespace = "my_package",
        resourceType = ResourceType.LAYOUT,
        resourceName = "my_name",
        qualifiedResourceName = "my_package:my_name"
      )

    parser.assertResourceUrl("@*my_package:layout/my_name")
      .hasAttributes(
        namespace = "my_package",
        resourceType = ResourceType.LAYOUT,
        resourceName = "my_name",
        qualifiedResourceName = "my_package:my_name"
      )

    parser.assertResourceUrl("@aapt:_aapt/34")
      .hasAttributes(
        namespace = "aapt",
        resourceType = ResourceType.AAPT,
        resourceName = "34",
        qualifiedResourceName = "aapt:34"
      )

    parser.assertResourceUrl("@android:style/invalid:reference")
      .hasAttributes(
        namespace = "android",
        resourceType = ResourceType.STYLE,
        resourceName = "invalid:reference",
        qualifiedResourceName = "android:invalid:reference"
      )
  }

  private fun ResourceUrlParser.assertResourceUrl(url: String): ResourceUrlParser {
    parseResourceUrl(url)
    return this
  }

  private fun ResourceUrlParser.hasAttributes(
    namespace: String?,
    resourceType: ResourceType?,
    resourceName: String,
    qualifiedResourceName: String
  ) {
    if (namespace != null) {
      assertThat(namespacePrefix).isEqualTo(namespace)
    } else {
      assertThat(namespacePrefix).isNull()
    }
    if (resourceType != null) {
      assertThat(type).isEqualTo(resourceType.getName())
    } else {
      assertThat(type).isNull()
    }
    assertThat(name).isEqualTo(resourceName)
    assertThat(qualifiedName).isEqualTo(qualifiedResourceName)
  }
}
