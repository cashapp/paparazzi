package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils
import com.android.utils.ILogger
import org.junit.Assert
import org.junit.Assume
import java.io.File
import javax.imageio.ImageIO

internal fun getGoldenImagePath(environment: Environment, snapshot: Snapshot): File =
        getGenericImagePath(environment.goldenImagesFolder, snapshot)

internal fun getGenericImagePath(rootFolder: String, snapshot: Snapshot): File = File(rootFolder,
        "${snapshot.testName.packageName}.${snapshot.testName.className}/${snapshot.testName.methodName}/${snapshot.name.sanitizeForFilename()}.png"
).also { it.parentFile.mkdirs() }

internal class PaparazziMediaVerifier(private val environment: Environment, private val logger: ILogger) : MediaVerifier {

    override fun verify(snapshot: Snapshot, generatedImageFile: File) {
        val logPrefix = "Snapshot [${snapshot}]:"
        if (generatedImageFile.extension.equals("png", ignoreCase = true)) {
            val goldenImageFile = getGoldenImagePath(environment, snapshot)
            if (goldenImageFile.exists()) {
                //verifying
                val diffFile = getGenericImagePath("${environment.reportDir}/diff", snapshot)
                if (ImageUtils.areImagesSimilar(
                                ImageIO.read(goldenImageFile),
                                ImageIO.read(generatedImageFile),
                                0.1,/*hard code, sigh*/
                                diffFile
                        )) {
                    logger.info("$logPrefix Generated image ${generatedImageFile.absolutePath} matches golden-image ${goldenImageFile.absolutePath}. Diff (if minor) is at ${diffFile.absolutePath}.")
                } else {
                    val errorMessage = "Snapshot [${snapshot}]: Generated image ${generatedImageFile.absolutePath} DOES NOT match golden-image ${goldenImageFile.absolutePath}. Diff is at ${diffFile.absolutePath}."
                    logger.warning(errorMessage)
                    Assert.fail(errorMessage)
                }
            } else {
                //since golden image does not exit, we'll ignore the test (with assumption)
                //and copy the image over.
                //TODO: I'm not sure this logic belongs to the verifier, but let's think about that later.
                generatedImageFile.copyTo(goldenImageFile, overwrite = true)
                val missingImageMessage = "Snapshot [${snapshot}]: Golden image was missing. Copied the generated image to ${goldenImageFile.absolutePath}. You should verify that it is okay."
                logger.warning(missingImageMessage)
                Assume.assumeTrue(missingImageMessage, false)
            }
        } else {
            logger.warning("Snapshot [${snapshot}]: Only supporting PNG snapshot verifications.")
        }
    }
}