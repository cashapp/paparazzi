package app.cash.paparazzi.binary

import org.junit.Assert
import org.junit.Test

class ParsersTests {
    @Test
    fun testHappyPathEntireResourcesList() {
        val parsedResources = ParsersTests::class.java.classLoader.getResourceAsStream("resources.arsc")?.use {
            parseResourcesArsc(it).entries.joinToString(separator = System.lineSeparator()) { (id, valuesMap) ->
                StringBuilder("- resource ")
                        .append(id.resourceString).append(" ")
                        .append("0x").append(id.id.toString(16))
                        .appendLine(":").also { builder ->
                            valuesMap.entries.forEach {
                                builder.append("    * ").append("configuration ").append(it.key).appendLine(":")
                                builder.append("      located under ").appendLine(it.value.resourceFilePath())
                                if (!it.value.isEncodedFileResource()) {
                                    builder.append("        value ").appendLine(it.value.toDataString())
                                }
                            }
                        }
            }
        } ?: error("resources.arsc was not found")

        val expect = ParsersTests::class.java.classLoader.getResourceAsStream("resources-dump.txt")?.use {
            it.reader().readText()
        }

        Assert.assertEquals(expect, parsedResources)
    }

    @Test
    fun testHappyPathValues() {
        val dumpedFileContents = ParsersTests::class.java.classLoader.getResourceAsStream("resources.arsc")?.use {
            valuesDump(encodedValues(parseResourcesArsc(it))).entries.joinToString(separator = System.lineSeparator()) { (path, fileContent) ->
                StringBuilder("path ").append(path)
                        .appendLine(":")
                        .append(fileContent)

            }
        } ?: error("resources.arsc was not found")

        val expect = ParsersTests::class.java.classLoader.getResourceAsStream("values-dump.txt")?.use {
            it.reader().readText()
        }

        Assert.assertEquals(expect, dumpedFileContents)
    }

    @Test
    fun testHappyPathPhysicalFiles() {
        val paths = ParsersTests::class.java.classLoader.getResourceAsStream("resources.arsc")?.use {
            physicalFilesPaths(parseResourcesArsc(it)).joinToString(separator = System.lineSeparator())
        } ?: error("resources.arsc was not found")

        val expect = ParsersTests::class.java.classLoader.getResourceAsStream("physical-files-dump.txt")?.use {
            it.reader().readText()
        }

        Assert.assertEquals(expect, paths)
    }

    @Test
    fun testParseXmlFile() {
        val decodedXml = ParsersTests::class.java.classLoader.getResourceAsStream("binary_encoded_launch.xml")?.use { encodedXml ->
            ParsersTests::class.java.classLoader.getResourceAsStream("resources.arsc")?.use { arsc ->
                parseXmlFile(encodedXml, parseResourcesArsc(arsc))
            } ?: error("resources.arsc was not found")
        } ?: error("binary_encoded_launch.xml was not found")

        val expect = ParsersTests::class.java.classLoader.getResourceAsStream("decoded_launch.xml")?.use {
            it.reader().readText()
        }

        Assert.assertEquals(expect, decodedXml)
    }
}