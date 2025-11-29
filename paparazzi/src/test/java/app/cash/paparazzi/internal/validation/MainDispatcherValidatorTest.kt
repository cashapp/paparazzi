package app.cash.paparazzi.internal.validation

import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherValidatorTest {

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `given strict mode true, when test dispatcher is used, then exception is thrown`() {
    val validator = MainDispatcherValidator(strictMode = true)

    Dispatchers.setMain(UnconfinedTestDispatcher())

    assertThrows(IllegalStateException::class.java) {
      validator.checkMainCoroutineDispatcher()
    }
  }

  @Test
  fun `given strict mode false, when main dispatcher is modified, then do not throw any exception`() {
    val nonStrictValidator = MainDispatcherValidator(strictMode = false)

    Dispatchers.setMain(StandardTestDispatcher())

    nonStrictValidator.checkMainCoroutineDispatcher()
  }
}
