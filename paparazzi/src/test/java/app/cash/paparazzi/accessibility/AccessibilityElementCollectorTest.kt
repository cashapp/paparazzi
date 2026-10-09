package app.cash.paparazzi.accessibility

import android.graphics.Rect
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
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
  fun `withTraversalNeighbors assigns before and after ids`() {
    val first = AccessibilityElement(
      id = "first",
      displayBounds = Rect(),
      mainAccessibilityText = "First"
    )
    val second = AccessibilityElement(
      id = "second",
      displayBounds = Rect(),
      mainAccessibilityText = "Second"
    )
    val third = AccessibilityElement(
      id = "third",
      displayBounds = Rect(),
      mainAccessibilityText = "Third"
    )

    val withNeighbors = collector
      .withTraversalNeighbors(linkedSetOf(first, second, third))
      .toList()

    assertThat(withNeighbors.map { it.id }).containsExactly("first", "second", "third").inOrder()
    assertThat(withNeighbors[0].beforeElementId).isNull()
    assertThat(withNeighbors[0].afterElementId).isEqualTo("second")
    assertThat(withNeighbors[1].beforeElementId).isEqualTo("first")
    assertThat(withNeighbors[1].afterElementId).isEqualTo("third")
    assertThat(withNeighbors[2].beforeElementId).isEqualTo("second")
    assertThat(withNeighbors[2].afterElementId).isNull()
  }

  @Test
  fun `withTraversalNeighbors assigns unique ids to elements with duplicate labels`() {
    val first = AccessibilityElement(
      id = "button",
      displayBounds = Rect(0, 0, 10, 10),
      mainAccessibilityText = "OK"
    )
    val second = AccessibilityElement(
      id = "button",
      displayBounds = Rect(0, 10, 10, 20),
      mainAccessibilityText = "OK"
    )

    val withNeighbors = collector
      .withTraversalNeighbors(listOf(first, second))
      .toList()

    assertThat(withNeighbors.map { it.id }).containsExactly("button#1", "button#2").inOrder()
    assertThat(withNeighbors[0].afterElementId).isEqualTo("button#2")
    assertThat(withNeighbors[1].beforeElementId).isEqualTo("button#1")
  }

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

  @Test
  fun `interactive container view group with children flags missing description for itself`() {
    val container = FrameLayout(paparazzi.context).apply {
      isClickable = true
      addView(
        TextView(context).apply {
          text = "Child Text"
        }
      )
    }
    val elements = collector.collect(container, null)

    assertThat(elements).hasSize(2)
    val containerElement = elements.first { it.id.startsWith("FrameLayout") }
    assertThat(containerElement.id).isEqualTo("FrameLayout($MISSING_DESCRIPTION_LABEL)")
    assertThat(containerElement.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(containerElement.isMissingDescription).isTrue()
    assertThat(containerElement.color).isEqualTo(RenderSettings.WARNING_COLOR)

    val childElement = elements.first { it.id.startsWith("TextView") }
    assertThat(childElement.contentDescription).isEqualTo("Child Text")
    assertThat(childElement.isMissingDescription).isFalse()
  }

  @Test
  fun `focusable container view group with children flags missing description for itself`() {
    val container = FrameLayout(paparazzi.context).apply {
      isFocusable = true
      addView(
        TextView(context).apply {
          text = "Child Text"
        }
      )
    }
    val elements = collector.collect(container, null)

    assertThat(elements).hasSize(2)
    val containerElement = elements.first { it.id.startsWith("FrameLayout") }
    assertThat(containerElement.isMissingDescription).isTrue()
    assertThat(containerElement.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `labeled interactive container view group does not flag missing description for itself`() {
    val container = FrameLayout(paparazzi.context).apply {
      isClickable = true
      contentDescription = "Container Label"
      addView(
        TextView(context).apply {
          text = "Child Text"
        }
      )
    }
    val elements = collector.collect(container, null)

    assertThat(elements).hasSize(2)
    val containerElement = elements.first { it.id.startsWith("FrameLayout") }
    assertThat(containerElement.contentDescription).isEqualTo("Container Label")
    assertThat(containerElement.isMissingDescription).isFalse()
  }

  @Test
  fun `scroll view container does not flag missing description for itself`() {
    val scrollView = ScrollView(paparazzi.context).apply {
      isFocusable = true
      addView(
        TextView(context).apply {
          text = "Child Text"
        }
      )
    }
    val elements = collector.collect(scrollView, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).isEqualTo("Child Text")
    assertThat(element.isMissingDescription).isFalse()
  }

  @Test
  fun `horizontal scroll view container does not flag missing description for itself`() {
    val horizontalScrollView = HorizontalScrollView(paparazzi.context).apply {
      isFocusable = true
      addView(
        TextView(context).apply {
          text = "Child Text"
        }
      )
    }
    val elements = collector.collect(horizontalScrollView, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).isEqualTo("Child Text")
    assertThat(element.isMissingDescription).isFalse()
  }

  @Test
  fun `unlabeled disabled button flags missing description`() {
    val button = Button(paparazzi.context).apply {
      isEnabled = false
    }
    val elements = collector.collect(button, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.id).isEqualTo("Button($MISSING_DESCRIPTION_LABEL)")
    assertThat(element.contentDescription).isEqualTo(MISSING_DESCRIPTION_LABEL)
    assertThat(element.isMissingDescription).isTrue()
    assertThat(element.color).isEqualTo(RenderSettings.WARNING_COLOR)
  }

  @Test
  fun `labeled disabled button does not flag missing description`() {
    val button = Button(paparazzi.context).apply {
      text = "Submit"
      isEnabled = false
    }
    val elements = collector.collect(button, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).contains("Submit")
    assertThat(element.isMissingDescription).isFalse()
  }

  @Test
  fun `unlabeled selected clickable view flags missing description`() {
    val customView = View(paparazzi.context).apply {
      isClickable = true
      isSelected = true
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
  fun `adapter view container does not flag missing description for itself`() {
    val listView = ListView(paparazzi.context).apply {
      isClickable = true
      isFocusable = true
      isLongClickable = true
    }
    val elements = collector.collect(listView, null)

    assertThat(elements).isEmpty()
  }

  @Test
  fun `labeled adapter view container has label and does not flag missing description`() {
    val listView = ListView(paparazzi.context).apply {
      isClickable = true
      contentDescription = "List Label"
    }
    val elements = collector.collect(listView, null)

    assertThat(elements).hasSize(1)
    val element = elements.first()
    assertThat(element.contentDescription).isEqualTo("List Label")
    assertThat(element.isMissingDescription).isFalse()
  }
}
