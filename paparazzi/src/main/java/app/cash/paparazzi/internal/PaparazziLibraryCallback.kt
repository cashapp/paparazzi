package app.cash.paparazzi.internal

import com.google.common.io.ByteStreams
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException

internal class PaparazziLibraryCallback(logger: PaparazziLogger, packageName: String) :
        PaparazziCallbackBase(logger, packageName) {

  override fun loadRClass(className: String): Class<*> = Class.forName("$packageName.R")

  override fun createXmlParserForFile(fileName: String): XmlPullParser? {
    try {
      FileInputStream(fileName).use { fileStream ->
        // Read data fully to memory to be able to close the file stream.
        val byteOutputStream = ByteArrayOutputStream()
        ByteStreams.copy(fileStream, byteOutputStream)
        val parser = KXmlParser()
        parser.setInput(ByteArrayInputStream(byteOutputStream.toByteArray()), null)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        return parser
      }
    } catch (e: IOException) {
      return null
    } catch (e: XmlPullParserException) {
      return null
    }
  }
}
