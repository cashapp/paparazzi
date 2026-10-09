package app.cash.paparazzi.internal

import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.internal.PaparazziLogger.MultipleFailuresException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.io.FileNotFoundException
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

class PaparazziLoggerTest {
  @Test
  fun testNoErrors() {
    val logger = PaparazziLogger()

    try {
      logger.assertNoErrors()
    } catch (ignored: Exception) {
      fail("Did not expect exception to be thrown: $ignored")
    }
  }

  @Test
  fun testSingleError() {
    val logger = PaparazziLogger()
    logger.error(FileNotFoundException("error1"), null)

    try {
      logger.assertNoErrors()
      fail("Expected exception to be thrown")
    } catch (ignored: Exception) {
      assertThat(ignored).isInstanceOf(FileNotFoundException::class.java)
    }
  }

  @Test
  fun testMultipleErrors() {
    val logger = PaparazziLogger()
    logger.error(FileNotFoundException("error1"), null)
    logger.error("tag", null, IllegalStateException("error2"), null, null)

    try {
      logger.assertNoErrors()
      fail("Expected exceptions to be thrown")
    } catch (ignored: Exception) {
      assertThat(ignored).isInstanceOf(MultipleFailuresException::class.java)
      assertThat(ignored.message).contains("There were 2 errors:")
      assertThat(ignored.message).contains("java.io.FileNotFoundException: error1")
      assertThat(ignored.message).contains("java.lang.IllegalStateException: error2")
    }
  }

  @Test
  fun testFlushErrors() {
    val logger = PaparazziLogger()
    logger.error(FileNotFoundException("error1"), null)
    logger.flushErrors()
    logger.assertNoErrors()
  }

  @Test
  fun filtersExpectedImageReaderMaxImagesFrameworkLog() {
    val logger = PaparazziLogger()
    val messages = capturePaparazziLogMessages {
      logger.logAndroidFramework(
        priority = 3,
        tag = "ImageReader_JNI",
        message = "Unable to acquire a buffer item, very likely client tried to acquire more than maxImages buffers"
      )
    }

    assertThat(messages).isEmpty()
  }

  @Test
  fun stillLogsOtherImageReaderFrameworkMessages() {
    val logger = PaparazziLogger()
    val messages = capturePaparazziLogMessages {
      logger.logAndroidFramework(
        priority = 3,
        tag = "ImageReader_JNI",
        message = "Input BufferItem or output LockedImage is NULL!"
      )
    }

    assertThat(messages).containsExactly(
      "ImageReader_JNI [3]: Input BufferItem or output LockedImage is NULL!"
    )
  }

  private fun capturePaparazziLogMessages(block: () -> Unit): List<String> {
    val javaLogger = Logger.getLogger(Paparazzi::class.java.name)
    val messages = mutableListOf<String>()
    val handler = object : Handler() {
      override fun publish(record: LogRecord) {
        messages += record.message
      }

      override fun flush() = Unit

      override fun close() = Unit
    }
    val previousUseParentHandlers = javaLogger.useParentHandlers
    javaLogger.useParentHandlers = false
    javaLogger.addHandler(handler)
    return try {
      block()
      messages
    } finally {
      javaLogger.removeHandler(handler)
      javaLogger.useParentHandlers = previousUseParentHandlers
    }
  }
}
