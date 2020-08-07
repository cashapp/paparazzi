package app.cash.paparazzi.internal

import net.dongliu.apk.parser.ApkFile
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLClassLoader

internal open class PaparazziBinaryResourcesCallback(logger: PaparazziLogger, packageName: String,
                                                     private val apkPath: File, private val rClassJarPath: URL) :
        PaparazziCallbackBase(logger, packageName) {

    override fun loadRClass(className: String): Class<*> {
        val urls: Array<URL> = arrayOf(URL("jar:${rClassJarPath}!/"))
        val ucl = URLClassLoader(urls)
        return Class.forName(className, true,   ucl)
    }

    override fun createXmlParserForFile(fileName: String): XmlPullParser? {
        try {
            ApkFile(apkPath).use { apkFile ->
                apkFile.transBinaryXml(fileName).let { xmlString ->
                    val parser = KXmlParser()
                    parser.setInput(ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8)), null)
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    return parser
                }
            }
        } catch (e: IOException) {
            return null
        } catch (e: XmlPullParserException) {
            return null
        }
    }
}
