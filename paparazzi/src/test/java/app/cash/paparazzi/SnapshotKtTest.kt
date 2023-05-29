package app.cash.paparazzi

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.Date

class SnapshotKtTest {
  @Test
  fun withName() {
    val path = Snapshot(
      name = "loading",
      testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
      timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate()
    ).goldenFile(File("/a/path"))

    assertThat(path)
      .isEqualTo(File("/a/path/app.cash.paparazzi_CelebrityTest_testSettings_loading.png"))
  }

  @Test
  fun withoutName() {
    val path = Snapshot(
      name = null,
      testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
      timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate()
    ).goldenFile(File("/a/path"))

    assertThat(path)
      .isEqualTo(File("/a/path/app.cash.paparazzi_CelebrityTest_testSettings.png"))
  }

  @Test
  fun withNameAndFrame() {
    val path = Snapshot(
      name = "loading",
      testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
      timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate()
    ).goldenFile(File("/a/path"), 0)

    assertThat(path)
      .isEqualTo(File("/a/path/app.cash.paparazzi_CelebrityTest_testSettings_loading_0.png"))
  }

  @Test
  fun withoutNameAndWithFrame() {
    val path = Snapshot(
      name = null,
      testName = TestName("app.cash.paparazzi", "CelebrityTest", "testSettings"),
      timestamp = Instant.parse("2019-03-20T10:27:43Z").toDate()
    ).goldenFile(File("/a/path"), 1)

    assertThat(path)
      .isEqualTo(File("/a/path/app.cash.paparazzi_CelebrityTest_testSettings_1.png"))
  }
}

private fun Instant.toDate() = Date(toEpochMilli())
