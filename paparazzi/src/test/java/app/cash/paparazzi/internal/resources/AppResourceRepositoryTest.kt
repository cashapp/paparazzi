package app.cash.paparazzi.internal.resources

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResourceRepositoryTest {
  @Test
  fun test() {
    val repository = AppResourceRepository.create(
      localResourceDirectories = listOf(resolveProjectPath("src/test/resources/folders/res").toFile()),
      moduleResourceDirectories = emptyList(),
      libraryRepositories = listOf(makeAarRepositoryFromExplodedAar("my_aar_lib"))
    )

    val map = repository.allResources
    assertThat(map.size).isEqualTo(45)

    assertThat(map[0].name).isEqualTo("slide_in_from_left")
    assertThat(map[0].type).isEqualTo(ResourceType.ANIM)
    assertThat(map[1].name).isEqualTo("test_animator")
    assertThat(map[1].type).isEqualTo(ResourceType.ANIMATOR)

    assertThat(map[4].name).isEqualTo("some_attr")
    assertThat(map[4].type).isEqualTo(ResourceType.ATTR)
    assertThat((map[4].resourceValue as AttrResourceValue).formats).isEqualTo(setOf(AttributeFormat.COLOR))

    assertThat(map[44].name).isEqualTo("test_network_security_config")
    assertThat(map[44].type).isEqualTo(ResourceType.XML)
  }
}
