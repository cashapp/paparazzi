package app.cash.paparazzi.accessibility

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchTargetValidatorTest {
  private fun element(width: Int, height: Int, id: String = "element") =
    AccessibilityElement(
      id = id,
      displayBounds = Rect(0, 0, width, height),
      contentDescription = id
    )

  // NEXUS_5 density is 3.0, so 144px == 48dp.
  private val validator = TouchTargetValidator(minTouchTargetSizeDp = 48f, density = 3f)

  @Test
  fun `interactive element at exactly 48x48dp is not flagged`() {
    val result = validator.validate(element(144, 144), isInteractive = true)
    assertTrue(result.isInteractive)
    assertFalse(result.isTouchTargetTooSmall)
  }

  @Test
  fun `interactive element below 48dp in either dimension is flagged`() {
    assertTrue(validator.validate(element(144, 143), true).isTouchTargetTooSmall)
    assertTrue(validator.validate(element(143, 144), true).isTouchTargetTooSmall)
    assertTrue(validator.validate(element(60, 60), true).isTouchTargetTooSmall)
  }

  @Test
  fun `non-interactive element is never flagged regardless of size`() {
    val result = validator.validate(element(60, 60), isInteractive = false)
    assertFalse(result.isInteractive)
    assertFalse(result.isTouchTargetTooSmall)
  }

  @Test
  fun `custom minimum touch target size is respected`() {
    val customValidator = TouchTargetValidator(minTouchTargetSizeDp = 20f, density = 3f)
    // 30x30dp is fine at a 20dp minimum...
    assertFalse(customValidator.validate(element(90, 90), true).isTouchTargetTooSmall)
    // ...while 10x10dp is not.
    assertTrue(customValidator.validate(element(30, 30), true).isTouchTargetTooSmall)
  }
}
