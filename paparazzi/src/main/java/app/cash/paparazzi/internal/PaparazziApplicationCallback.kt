package app.cash.paparazzi.internal

import app.cash.paparazzi.Environment
import com.android.ide.common.rendering.api.ResourceNamespace.ANDROID
import com.android.ide.common.rendering.api.ResourceReference
import java.io.File
import java.util.zip.ZipFile

internal open class PaparazziApplicationCallback :
        PaparazziCallback {
    constructor(logger: PaparazziLogger, environment: Environment): super(logger, environment)
    constructor(logger: PaparazziLogger, environment: Environment, projectResources: Map<Int, ResourceReference>): super(logger, environment, projectResources)

    private val apkEntriesSupport = ApkEntriesSupport(environment.apkPath,
            projectResources,
            readResources(logger, Class.forName("android.R"), ANDROID))

    init {
        //dumping apk res into resDir
        dumpFromApk("res/", environment.resDir)
        dumpFromApk("assets/", environment.assetsDir)
        //copying merged values.xml files into res folder
        File(environment.mergedResourceValueDir)
                .also { resourcesRoot ->
                    resourcesRoot
                            .walkTopDown()
                            .forEach { resourceFile ->
                                val target = File(environment.resDir, resourceFile.relativeTo(resourcesRoot).path)
                                if (resourceFile.isDirectory) {
                                    target.mkdirs()
                                } else {
                                    resourceFile.copyTo(target, overwrite = true)
                                }
                            }
                }
    }

    private fun dumpFromApk(sourceFolder: String, targetFolder: String) {
        File(targetFolder).deleteRecursively()
        ZipFile(apkEntriesSupport.apkPath).entries().asSequence()
                .filter { it.name.startsWith(sourceFolder) }
                .filter { !it.isDirectory }
                .forEach {
                    val targetToCreate = File(targetFolder, it.name.substring(sourceFolder.length))
                    targetToCreate.parentFile.mkdirs()
                    val source = if (it.name.endsWith(".xml"))
                        apkEntriesSupport.readXmlFileFromApk(it.name)
                    else
                        apkEntriesSupport.readFileFromApk(it.name)
                    source?.use { it.copyTo(targetToCreate.outputStream()) }
                }
    }
}
