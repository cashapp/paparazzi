package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.resources.ResourceFolderRepository
import app.cash.paparazzi.internal.resources.resolveProjectPath
import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test

@Ignore("enable when ResourceFolderRepository integrates with resource loading")
class ResourceFolderRepositoryTest {
  private val resFolderRoot = resolveProjectPath("src/test/resources/folders/res")

  @Test
  fun test() {
    val repository = ResourceFolderRepository(
      resFolderRoot.toFile(),
      ResourceNamespace.TODO()
    )

    val map = repository.allResources

    // COLORS
    assertThat(map[0].name).isEqualTo("test_color_2")
    assertThat(map[0].resourceValue.value).isEqualTo("#00000000")
    assertThat(map[1].name).isEqualTo("test_color")
    assertThat(map[1].resourceValue.value).isEqualTo("#ffffffff")

    // BOOLS
    assertThat(map[0].name).isEqualTo("screen_small")
    assertThat(map[0].resourceValue.value).isEqualTo("true")
    assertThat(map[1].name).isEqualTo("adjust_view_bounds")
    assertThat(map[1].resourceValue.value).isEqualTo("false")

    // DIMENS
    assertThat(map[0].name).isEqualTo("textview_height")
    assertThat(map[0].resourceValue.value).isEqualTo("25dp")
    assertThat(map[1].name).isEqualTo("textview_width")
    assertThat(map[1].resourceValue.value).isEqualTo("150dp")

    // IDS XML
    assertThat(map[0].name).isEqualTo("dialog_exit")
    assertThat(map[0].type).isEqualTo(ResourceType.ID)
    assertThat(map[1].name).isEqualTo("button_ok")
    assertThat(map[1].type).isEqualTo(ResourceType.ID)

    // INTEGERS XML
    assertThat(map[0].name).isEqualTo("max_speed")
    assertThat(map[0].resourceValue.value).isEqualTo("75")
    assertThat(map[1].name).isEqualTo("min_speed")
    assertThat(map[1].resourceValue.value).isEqualTo("5")

    // STRINGS XML
    val array = map[0].resourceValue as ArrayResourceValue
    val plurals = map[1].resourceValue as PluralsResourceValue
    val string = map[2].resourceValue

    val firstItemInArray = array.getElement(0)
    val secondItemInArray = array.getElement(1)

    assertThat(array.name).isEqualTo("string_array_name")
    assertThat(plurals.name).isEqualTo("plural_name")
    assertThat(string.name).isEqualTo("string_name")

    assertThat(string.value).isEqualTo("Test String")

    assertThat(firstItemInArray).isEqualTo("First Test String")
    assertThat(secondItemInArray).isEqualTo("Second Test String")

    assertThat(plurals.getQuantity(0)).isEqualTo("zero")
    assertThat(plurals.getValue(0)).isEqualTo("Nothing")
    assertThat(plurals.getQuantity(1)).isEqualTo("one")
    assertThat(plurals.getValue(1)).isEqualTo("One String")

    assertThat(string.value).isEqualTo("Test String")

    // STYLE XML
    val name = map[0].name
    val value = map[0].resourceValue as StyleResourceValue
    val firstItem = value.definedItems.elementAt(0)
    val secondItem = value.definedItems.elementAt(1)

    assertThat(name).isEqualTo("TestStyle")
    assertThat(firstItem.attrName).isEqualTo("android:scrollbars")
    assertThat(firstItem.value).isEqualTo("horizontal")
    assertThat(secondItem.attrName).isEqualTo("android:marginTop")
    assertThat(secondItem.value).isEqualTo("16dp")

    // ATTRS XML
    val firstAttr = map[0].resourceValue as AttrResourceValue
    val secondAttr = map[1].resourceValue as AttrResourceValue
    val styleable = map[2]
    assertThat(map.size).isEqualTo(3)
    assertThat(styleable.name).isEqualTo("test_styleable")
    assertThat(firstAttr.name).isEqualTo("TestAttrInt")
    assertThat(secondAttr.name).isEqualTo("TestAttr")
    assertThat(firstAttr.formats).isEqualTo(setOf(AttributeFormat.INTEGER))
    assertThat(secondAttr.formats).isEqualTo(setOf(AttributeFormat.FLOAT))

    // LAYOUT XML
    val firstId = map[0].resourceValue as ResourceValue
    val secondId = map[1].resourceValue as ResourceValue
    assertThat(firstId.name).isEqualTo("test_view")
    assertThat(secondId.name).isEqualTo("test_layout")

    // DRAWABLE
    val resource = map[0].resourceValue as ResourceValue
    assertThat(resource.name).isEqualTo("ic_android_black_24dp")
    assertThat(resource.resourceType).isEqualTo(ResourceType.DRAWABLE)

    // ANIM
    val resource2 = map[0].resourceValue as ResourceValue
    assertThat(resource2.name).isEqualTo("slide_in_from_left")
    assertThat(resource2.resourceType).isEqualTo(ResourceType.ANIM)

    // COLOR
    val resource3 = map[0].resourceValue as ResourceValue
    assertThat(resource3.name).isEqualTo("color_selector")
    assertThat(resource3.resourceType).isEqualTo(ResourceType.COLOR)

    // ANIMATOR
    val resource4 = map[0].resourceValue as ResourceValue
    assertThat(resource4.name).isEqualTo("test_animator")
    assertThat(resource4.resourceType).isEqualTo(ResourceType.ANIMATOR)

    // MIPMAP
    val resource5 = map[0].resourceValue as ResourceValue
    assertThat(resource5.name).isEqualTo("ic_launcher")
    assertThat(resource5.resourceType).isEqualTo(ResourceType.MIPMAP)

    // MENU
    val firstId2 = map[0].resourceValue as ResourceValue
    val secondId2 = map[1].resourceValue as ResourceValue
    assertThat(firstId2.name).isEqualTo("test_menu_1")
    assertThat(secondId2.name).isEqualTo("test_menu_2")

    // XML
    val resource6 = map[0].resourceValue as ResourceValue
    assertThat(resource6.name).isEqualTo("test_network_security_config")
    assertThat(resource6.resourceType).isEqualTo(ResourceType.XML)

    // RAW
    val resource7 = map[0].resourceValue as ResourceValue
    assertThat(resource7.name).isEqualTo("test_json")
    assertThat(resource7.resourceType).isEqualTo(ResourceType.RAW)

    // FONT
    val resource8 = map[0].resourceValue as ResourceValue
    assertThat(resource8.resourceType).isEqualTo(ResourceType.FONT)
  }
}
