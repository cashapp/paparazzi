package app.cash.paparazzi

import java.util.Locale

public interface FileNameProvider {
  public fun snapshotFileName(snapshot: Snapshot, extension: String): String
}

internal class DefaultFileNameProvider(
  private val delimiter: String = "_"
) : FileNameProvider {

  override fun snapshotFileName(snapshot: Snapshot, extension: String): String {
    val name = snapshot.name
    val formattedLabel = if (name != null) {
      "$delimiter${name.lowercase(Locale.US).replace("\\s".toRegex(), delimiter)}"
    } else {
      ""
    }

    val testName = snapshot.testName
    return "${testName.packageName}${delimiter}${testName.className}${delimiter}${testName.methodName}$formattedLabel.$extension"
  }
}
