package app.cash.paparazzi.accessibility

import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AccessibilityElementCollectorTest {
  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

  private val collector = AccessibilityElementCollector()

  @Test
  fun `unlabeled interactive button flags missing description`() {
    val button = Button(paparazzi.context)
    val elements = collector.collect(button, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("Button($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `labeled button does not flag missing description`() {
    val button = Button(paparazzi.context).apply {
      text = "Submit"
    }
    val elements = collector.collect(button, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).isEqualTo("Submit")
    assertThat(element.isMissingDescription).isFalse()
  }

  @Test
  fun `unlabeled clickable image view flags missing description`() {
    val imageView = ImageView(paparazzi.context).apply {
      isClickable = true
    }
    val elements = collector.collect(imageView, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("ImageView($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `decorative image view without description is not flagged`() {
    val imageView = ImageView(paparazzi.context)
    val elements = collector.collect(imageView, null)

    assertThat(elements).isEmpty()
  }

  @Test
  fun `explicitly decorative image view with importantForAccessibility NO is not flagged`() {
    val imageView = ImageView(paparazzi.context).apply {
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val elements = collector.collect(imageView, null)

    assertThat(elements).isEmpty()
  }

  @Test
  fun `informative image view marked importantForAccessibility YES flags missing description`() {
    val imageView = ImageView(paparazzi.context).apply {
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    val elements = collector.collect(imageView, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("ImageView($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `unlabeled image button flags missing description`() {
    val imageButton = ImageButton(paparazzi.context)
    val elements = collector.collect(imageButton, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("ImageButton($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `unlabeled check box flags missing description`() {
    val checkBox = CheckBox(paparazzi.context)
    val elements = collector.collect(checkBox, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("CheckBox($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `labeled check box does not flag missing description`() {
    val checkBox = CheckBox(paparazzi.context).apply {
      text = "Agree"
    }
    val elements = collector.collect(checkBox, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).contains("Agree")
    assertThat(element.isMissingDescription).isFalse()
  }

  @Test
  fun `clickable view flags missing description`() {
    val customView = View(paparazzi.context).apply {
      isClickable = true
    }
    val elements = collector.collect(customView, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("View($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `container view group does not flag missing description for itself`() {
    val container = LinearLayout(paparazzi.context).apply {
      addView(
        TextView(context).apply {
          text = "Child Text"
        }
      )
    }
    val elements = collector.collect(container, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).isEqualTo("Child Text")
    assertThat(element.isMissingDescription).isFalse()
  }
}
