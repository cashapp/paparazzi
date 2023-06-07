package app.cash.paparazzi.internal.resources

import com.android.resources.ResourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ProjectResourceRepositoryTest {
  @Test
  fun test() {
    // TODO: need mapOf(package to listOf(resourceDirectory)) for each transitive project module
    val repository = ProjectResourceRepository.create(
      resourceDirectories = listOf(File("src/test/resources/folders/res"))
    )

    val map = repository.allResources
    assertThat(map.size).isEqualTo(31)

    assertThat(map[0].name).isEqualTo("slide_in_from_left")
    assertThat(map[0].type).isEqualTo(ResourceType.ANIM)
    assertThat(map[1].name).isEqualTo("test_animator")
    assertThat(map[1].type).isEqualTo(ResourceType.ANIMATOR)

    assertThat(map[30].name).isEqualTo("test_network_security_config")
    assertThat(map[30].type).isEqualTo(ResourceType.XML)
  }
}
