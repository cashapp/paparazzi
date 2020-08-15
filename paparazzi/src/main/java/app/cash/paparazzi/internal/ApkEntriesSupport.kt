package app.cash.paparazzi.internal

import android.util.TypedValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import net.dongliu.apk.parser.parser.BinaryXmlParser
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.parser.XmlTranslator
import net.dongliu.apk.parser.struct.resource.ResourceTable
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.ZipFile

/*Using empty table here since we want to do the 'resourceId' resolving*/
private val emptyResourceTable = ResourceTable()

internal class ApkEntriesSupport(
        internal val apkPath: String,
        projectResourceMap: Map<Int, ResourceReference>,
        androidResourceMap: Map<Int, ResourceReference>) {

    /**
     * This will hold a map of ID->resource which was parsed from the APK.
     * We need this to support synthetic resources (aapt)
     */
    private val resourceTableFromApk = readFileFromApk("resources.arsc")!!.readBytes()
            .let { ByteBuffer.wrap(it) }
            .let { ResourceTableParser(it) }
            .let {
                it.parse()
                it.resourceTable
            }

    private val resourcesMap = projectResourceMap.plus(androidResourceMap)

    internal fun readFileFromApk(pathInApk: String): InputStream? {
        println("readFileFromApk $apkPath / $pathInApk")
        return ZipFile(apkPath).let { zipFile ->
            zipFile.getEntry(pathInApk)?.let {
                zipFile.getInputStream(it)
            }
        }
    }

    internal fun readXmlFileFromApk(pathInApk: String): InputStream? {
        readFileFromApk(pathInApk).use {
            it?.readBytes().let { bytes ->
                val xmlTranslator = XmlTranslator()
                val binaryXmlParser = BinaryXmlParser(ByteBuffer.wrap(bytes), emptyResourceTable)
                binaryXmlParser.xmlStreamer = xmlTranslator
                binaryXmlParser.parse()
                return xmlTranslator.xml
                        .also {
                            println("xml from APK: $it")
                        }.let {
                            fixRougeRawReference(it)
                        }.also {
                            println("un-rouge-raw-xml from APK: $it")
                        }.let {
                            fixRougeDimenReference(it)
                        }.also {
                            println("un-dimen-raw-xml from APK: $it")
                        }.let {
                            fixResourceRefInXml(it)
                        }.also {
                            println("fixed-xml from APK: $it")
                        }.byteInputStream()
            }
        }
    }

    private fun fixRougeRawReference(xmlString: String) = xmlString
            .replace(Regex("\"\\{(\\d+):([0123456789]+)}\"")) { match ->
                //ApkParser does not handle ATTRIBUTE (0x02) and FLOAT (0x04) attribute values.
                //we'll need to fix that for them.
                when (match.groupValues[1].toInt()) {
                    0x02 -> "\"resourceId:0x${match.groupValues[2].toInt().toString(16)}\""
                    0x04 -> "\"${Float.fromBits(match.groupValues[2].toInt())}\""
                    else -> error("I did not expect raw-value of type ${match.groupValues[1]}")
                }
            }.replace(Regex("\"([0123456789\\-+.E]+)(%[p]?)\"")) { match ->
                //FRACTION is not represented correctly in apk-parser:
                // they shift-right were they should mask the first four bits.
                // We'll try to reformat (this should work, but parsing from string may be off)
                val reformattedFloatBits = match.groupValues[1]
                        .toFloat()
                        .toBits()
                        .shl(4)

                "\"${Float.fromBits(reformattedFloatBits)}${match.groupValues[2]}\""
            }

    private fun fixRougeDimenReference(xmlString: String) = xmlString
            .replace(Regex("\"(\\d+)unknown unit:0x([01234567890abcdef]+)\"")) { match ->
                //ApkParser does not handle fractions in dimensions correctly.
                //we'll need to fix that for them.
                val wrongValue = match.groupValues[1].toInt()
                val wrongType = match.groupValues[2].toInt(16)
                val fullValue = wrongValue.shl(8) + wrongType
                val unitPostFix = fullValue.and(TypedValue.COMPLEX_UNIT_MASK).let {
                    when (it) {
                        TypedValue.COMPLEX_UNIT_PX -> "px"
                        TypedValue.COMPLEX_UNIT_DIP -> "dp"
                        TypedValue.COMPLEX_UNIT_SP -> "sp"
                        TypedValue.COMPLEX_UNIT_PT -> "pt"
                        TypedValue.COMPLEX_UNIT_IN -> "in"
                        TypedValue.COMPLEX_UNIT_MM -> "mm"
                        else -> error("I have not idea what unit is ${it}!")
                    }
                }

                "\"${TypedValue.complexToFloat(fullValue)}${unitPostFix}\""
            }

    private fun generateFromResourceClass(id: Int) =
            (resourcesMap[id]
                    ?: error("this should not happen with ID $id")).let { resourceReference ->
                val referenceString = StringBuilder()
                if (resourceReference.resourceType.canBeReferenced)
                    referenceString.append("@")
                else
                    referenceString.append("?")

                if (resourceReference.namespace == ResourceNamespace.ANDROID)
                    referenceString.append("android:")
                else if (resourceReference.resourceType == ResourceType.ID)
                //this is required for local ID resources. May be an issue with layout-lib
                    referenceString.append("+")

                referenceString.append(resourceReference.resourceType.getName())
                        .append("/")
                        .append(resourceReference.resourceUrl.name)
                referenceString.toString()
            }

    private fun generateFromResourceTable(id: Int) =
            resourceTableFromApk.getResourcesById(id.toLong())
                    .first()
                    .let { resource ->
                        println("resource from table: ${resource.type.name}, ${resource.type.id}, ${resource.typeSpec.name} ${resource.typeSpec.id}, ${resource.resourceEntry.toStringValue(resourceTableFromApk, Locale.ROOT)} ${resource.resourceEntry.key}")
                        "@${resource.type.name}:${resource.resourceEntry.key}"
                    }

    private fun fixResourceRefInXml(xmlString: String) =
            xmlString.replace(Regex("\"resourceId:0x([0123456789abcdef]+)\"")) { match ->
                match.groupValues[1].toInt(16).let { id ->
                    when (id) {
                        0 -> "@null"
                        in resourcesMap -> generateFromResourceClass(id)
                        in resourceTableFromApk -> generateFromResourceTable(id)
                        else -> error("Could not find resource ID $id anywhere.")
                    }
                }.let { "\"$it\"" }
            }

    private operator fun ResourceTable.contains(id: Int) = getResourcesById(id.toLong()).isNotEmpty()
}
