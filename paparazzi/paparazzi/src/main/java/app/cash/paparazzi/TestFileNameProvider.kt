package app.cash.paparazzi

import app.cash.paparazzi.SnapshotHandler.FileNameProvider
import java.util.Locale

class TestFileNameProvider : FileNameProvider {
  lateinit var testName: TestName
  override fun computeFileName(snapshot: Snapshot, extension: String, delimiter: String): String {
    val formattedLabel = if (snapshot.name != null) {
      "$delimiter${snapshot.name.lowercase(Locale.US).replace("\\s".toRegex(), delimiter)}"
    } else {
      ""
    }
    return "${testName.packageName}${delimiter}${testName.className}${delimiter}${testName.methodName}$formattedLabel.$extension"
  }
}
