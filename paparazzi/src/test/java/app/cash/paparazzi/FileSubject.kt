package app.cash.paparazzi

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File

internal class FileSubject private constructor(
  metadata: FailureMetadata,
  private val actual: File?
) : Subject(metadata, actual) {
  fun hasContent(expected: String) {
    assertThat(actual).isNotNull()
    requireNotNull(actual) // smart cast

    assertThat(actual.readText()).isEqualTo(expected)
  }

  fun isEmptyDirectory() {
    assertThat(actual).isNotNull()
    requireNotNull(actual) // smart cast

    assertWithMessage("File $actual is not a directory").that(actual.isDirectory).isTrue()
    assertWithMessage("Directory $actual is not empty")
      .that(actual.listFiles())
      .isEmpty()
  }

  fun exists() {
    assertThat(actual).isNotNull()
    require(actual is File) // smart cast

    assertWithMessage("File $actual does not exist").that(actual.exists()).isTrue()
  }

  fun doesNotExist() {
    assertThat(actual).isNotNull()
    require(actual is File) // smart cast

    assertWithMessage("File $actual does exist").that(actual.exists()).isFalse()
  }

  companion object {
    private val FILE_SUBJECT_FACTORY = Factory<FileSubject, File> { metadata, actual ->
      FileSubject(metadata, actual)
    }

    fun assertThat(actual: File?): FileSubject {
      return assertAbout(FILE_SUBJECT_FACTORY).that(actual)
    }
  }
}
