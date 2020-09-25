package app.cash.paparazzi.binary

import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceConfiguration
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceValue
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import com.google.devrel.gmscore.tools.apk.arsc.XmlChunk
import com.google.devrel.gmscore.tools.apk.arsc.XmlEndElementChunk
import com.google.devrel.gmscore.tools.apk.arsc.XmlNamespaceStartChunk
import com.google.devrel.gmscore.tools.apk.arsc.XmlStartElementChunk
import org.w3c.dom.Node
import java.io.InputStream
import java.io.StringWriter
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


data class ResourceIdentifier(val id: Int, val type: String, val name: String) {
    val resourceString = "@${if (type == "id") "+id" else type}/${name}"
}

fun parseResourcesArsc(inputStream: InputStream): Map<ResourceIdentifier, Map<BinaryResourceConfiguration, TypeChunk.Entry>> {
    val tableChunk = BinaryResourceFile.fromInputStream(inputStream).chunks.single() as ResourceTableChunk
    val packageChunk = tableChunk.packages.single() as PackageChunk

    val allResources = mutableMapOf<ResourceIdentifier, MutableMap<BinaryResourceConfiguration, TypeChunk.Entry>>()

    packageChunk.typeChunks.forEach { typeChunk ->
        typeChunk.entries.forEach {
            val resId = calculateResourceId(packageChunk.id, typeChunk.id, it.key)
            allResources.getOrPut(ResourceIdentifier(resId, typeChunk.typeName, it.value.key())) { mutableMapOf() }[typeChunk.configuration] = it.value
        }
    }

    return allResources
}

fun encodedValues(resourcesMap: Map<ResourceIdentifier, Map<BinaryResourceConfiguration, TypeChunk.Entry>>): Map<Path, List<TypeChunk.Entry>> =
    resourcesMap
            .map { it.value }
            .flatMap { it.values }
            .filter { !it.isEncodedFileResource() }
            .groupBy { it.resourceFilePath() }
            .mapKeys { Paths.get(it.key) }

fun physicalFilesPaths(resourcesMap: Map<ResourceIdentifier, Map<BinaryResourceConfiguration, TypeChunk.Entry>>): List<Path> =
    resourcesMap
            .map { it.value }
            .flatMap { it.values }
            .filter { it.isEncodedFileResource() }
            .map { it.resourceFilePath() }
            .map { Paths.get(it) }
            .toList()

fun valuesDump(values: Map<Path, List<TypeChunk.Entry>>): Map<Path, String> {
    return values.map {
        it.key to it.value.let { entries ->
            StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>").appendLine()
                    .appendLine("<resources>")
                    //<item type="[type name]" name="[resource name]">[resource value]</item>
                    .also { builder ->
                        entries.forEach { entry ->
                            builder.append("    <item type=\"").append(entry.typeName()).append("\" name=\"").append(entry.key()).append("\"")
                            if (entry.typeName() == "id" && entry.toDataString() == "false"/*which means the ID was not hard coded.*/) {
                                builder.append("/>")
                            } else {
                                builder.append(">").append(entry.toDataString()).append("</item>")
                            }
                            builder.appendLine()
                        }
                    }
                    .appendLine("</resources>")
        }
    }
            .map { pair -> pair.first to pair.second.toString() }
            .toMap()
}

internal enum class ComplexUnit(val unitType: Byte) {
    // TYPE_DIMENSION: Value is raw pixels.
    COMPLEX_UNIT_PX(0),

    // TYPE_DIMENSION: Value is Device Independent Pixels.
    COMPLEX_UNIT_DIP(1),

    // TYPE_DIMENSION: Value is a Scaled device independent Pixels.
    COMPLEX_UNIT_SP(2),

    // TYPE_DIMENSION: Value is in points.
    COMPLEX_UNIT_PT(3),

    // TYPE_DIMENSION: Value is in inches.
    COMPLEX_UNIT_IN(4),

    // TYPE_DIMENSION: Value is in millimeters.
    COMPLEX_UNIT_MM(5);

    companion object {
        fun fromInt(type: Int): ComplexUnit {
            val byteValue = type.shl(COMPLEX_UNIT_SHIFT).and(COMPLEX_UNIT_MASK).toByte()
            return values().find { it.unitType == byteValue }
                    ?: error("can not find enum value for $type / $byteValue")
        }

        // Where the unit type information is.  This gives us 16 possible
        // types, as defined below.
        private const val COMPLEX_UNIT_SHIFT = 0
        private const val COMPLEX_UNIT_MASK = 0xf
    }
}

private fun calculateResourceId(packageId: Int, typeId: Int, entryId: Int): Int {
    // See BinaryResourceIdentifier for the source of this algorithm.
    return ((packageId and 0xFF) shl 24) or
            ((typeId and 0xFF) shl 16) or
            (entryId and 0xFFFF)
}

private fun Chunk.getResourceTableChunk(): ResourceTableChunk {
    var chunk: Chunk? = parent
    while (chunk != null && chunk !is ResourceTableChunk) {
        chunk = chunk.parent
    }
    return chunk as ResourceTableChunk
}

/**
 * Path location where the data of this entry is located.
 * Either a path to a file, (if isEncodedFileResource() returns true), or path to a `values.xml`
 *
 */
internal fun TypeChunk.Entry.resourceFilePath(): String {
    return if (isEncodedFileResource()) {
        toDataString()
    } else {
        val pathBuilder = StringBuilder("res/")
        pathBuilder.append("values")/*those are always under the 'values' folder*/
        if (!parent().configuration.isDefault) {
            pathBuilder
                    .append('-')
                    .append(parent().configuration.toString())
        }
        pathBuilder
                .append('/')
                .append(typeName()).append("s")//plural
                .append(".xml")
                .toString()
    }
}

internal fun TypeChunk.Entry.isEncodedFileResource(): Boolean {
    return when (val type = value()?.type()) {
        /*all these cases are encoded inside the resources file*/
        BinaryResourceValue.Type.NULL,
        BinaryResourceValue.Type.REFERENCE,
        BinaryResourceValue.Type.ATTRIBUTE,
        BinaryResourceValue.Type.FLOAT,
        BinaryResourceValue.Type.DIMENSION,
        BinaryResourceValue.Type.FRACTION,
        BinaryResourceValue.Type.INT_DEC,
        BinaryResourceValue.Type.INT_HEX,
        BinaryResourceValue.Type.INT_BOOLEAN,
        BinaryResourceValue.Type.INT_COLOR_ARGB8,
        BinaryResourceValue.Type.INT_COLOR_RGB8,
        BinaryResourceValue.Type.INT_COLOR_ARGB4,
        BinaryResourceValue.Type.INT_COLOR_RGB4 -> false
        BinaryResourceValue.Type.STRING -> when (this.typeName()) {
            "string" -> false /*the mapping is encoded inside the resources file*/
            else -> true /*points to the location in the APK.*/
        }
        BinaryResourceValue.Type.DYNAMIC_REFERENCE -> TODO(type.name)
        BinaryResourceValue.Type.DYNAMIC_ATTRIBUTE -> TODO(type.name)
        null -> error("This should not happen.")
    }
}

private val RADIX_MULTS = floatArrayOf(0.00390625f, 3.0517578E-5f, 1.1920929E-7f, 4.656613E-10f)

private fun BinaryResourceValue.toXmlRepresentation(): String {
    return when (type()) {
        BinaryResourceValue.Type.NULL -> "@null"
        BinaryResourceValue.Type.REFERENCE,
        BinaryResourceValue.Type.ATTRIBUTE -> "resourceId:0x${data().toString(16)}"
        BinaryResourceValue.Type.FLOAT -> Float.fromBits(data()).toString()
        BinaryResourceValue.Type.DIMENSION -> data().let {
            //conversion of complexToFloat was taken from android.util.TypedValue.complexToFloat
            val numberValue = (data().and(-256)) * RADIX_MULTS[data().shr(4).and(3)]
            when (ComplexUnit.fromInt(it)) {
                ComplexUnit.COMPLEX_UNIT_PX -> "${numberValue}px"
                ComplexUnit.COMPLEX_UNIT_DIP -> "${numberValue}dp"
                ComplexUnit.COMPLEX_UNIT_SP -> "${numberValue}sp"
                ComplexUnit.COMPLEX_UNIT_PT -> "${numberValue}pt"
                ComplexUnit.COMPLEX_UNIT_IN -> "${numberValue}in"
                ComplexUnit.COMPLEX_UNIT_MM -> "${numberValue}mm"
            }
        }
        BinaryResourceValue.Type.FRACTION -> data().let {
            "${Float.fromBits(it)}${if (it.and(0xf) == 0) "%" else "%p"}"
        }

        BinaryResourceValue.Type.INT_DEC -> data().toString()
        BinaryResourceValue.Type.INT_HEX -> "0x${data().toString(16)}"
        BinaryResourceValue.Type.INT_BOOLEAN -> (data() != 0).toString()
        BinaryResourceValue.Type.INT_COLOR_ARGB8 -> String.format("#%08x", data())
        BinaryResourceValue.Type.INT_COLOR_ARGB4 -> String.format("#%04x", data())
        BinaryResourceValue.Type.INT_COLOR_RGB8 -> String.format("#%06x", data())
        BinaryResourceValue.Type.INT_COLOR_RGB4 -> String.format("#%03x", data())
        else -> error("Not supporting ${type()} just yet.")
    }
}

internal fun TypeChunk.Entry.toDataString(): String {
    if (isComplex) {
        error("Not supporting this right now")
    } else {
        return value()!!.let {
            if (it.type() == BinaryResourceValue.Type.STRING) {
                if (it.data() < 0) {
                    "@null"
                } else {
                    parent().getResourceTableChunk().stringPool.getString(it.data())
                }
            } else {
                it.toXmlRepresentation()
            }
        }
    }
}

private fun Map<ResourceIdentifier, Map<BinaryResourceConfiguration, TypeChunk.Entry>>.getByResId(id: Int): ResourceIdentifier? {
    return this.keys.find { it.id == id }
}

fun parseXmlFile(inputStream: InputStream, resources: Map<ResourceIdentifier, Map<BinaryResourceConfiguration, TypeChunk.Entry>>): String {
    //taken from https://github.com/JakeWharton/diffuse/blob/96f9e42952f0bb343d627a08d55c037cd9bb9d77/diffuse/src/main/kotlin/com/jakewharton/diffuse/Manifest.kt#L64
    val rootChunk = BinaryResourceFile.fromInputStream(inputStream).chunks.single() as XmlChunk
    val document = DocumentBuilderFactory.newInstance()!!.apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .newDocument()

    val nodeStack = mutableListOf<Node>().apply { add(document) }
    val namespacesToAdd = mutableMapOf<String, String>()
    val namespacesInScope = mutableMapOf<String?, String>(null to "")
    rootChunk.chunks.values.forEach { chunk ->
        when (chunk) {
            is XmlNamespaceStartChunk -> {
                namespacesToAdd[chunk.prefix] = chunk.uri
                namespacesInScope[chunk.uri] = "${chunk.prefix}:"
            }
            is XmlStartElementChunk -> {
                val canonicalNamespace = chunk.namespace.takeIf(String::isNotEmpty)
                val canonicalName = "${namespacesInScope[canonicalNamespace]}${chunk.name}"
                val element = document.createElementNS(canonicalNamespace, canonicalName)
                if (namespacesToAdd.isNotEmpty()) {
                    namespacesToAdd.forEach { (prefix, uri) ->
                        element.setAttribute("xmlns:$prefix", uri)
                    }
                    namespacesToAdd.clear()
                }

                for (attribute in chunk.attributes) {
                    val attributeNamespace = attribute.namespace().takeIf(String::isNotEmpty)
                    val attributeName = "${namespacesInScope[attributeNamespace]}${attribute.name()}"

                    val typedValue = attribute.typedValue()
                    val attributeValue = when (typedValue.type()) {
                        BinaryResourceValue.Type.STRING -> attribute.rawValue()
                        BinaryResourceValue.Type.REFERENCE -> resources.getByResId(typedValue.data())?.resourceString
                                ?: attribute.rawValue()
                        else -> typedValue.toXmlRepresentation()
                    }

                    element.setAttributeNS(attributeNamespace, attributeName, attributeValue)
                }
                nodeStack.first().appendChild(element)
                nodeStack.add(0, element)
            }
            is XmlEndElementChunk -> {
                nodeStack.removeAt(0)
            }
        }
    }

    val transformer: Transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        setOutputProperty(OutputKeys.METHOD, "xml")
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
    }

    return StringWriter().also {
        transformer.transform(DOMSource(document), StreamResult(it))
    }.toString()
}