package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils
import com.android.utils.ILogger
import org.junit.Assert
import java.io.File
import javax.imageio.ImageIO

internal fun getGoldenImagePath(environment: Environment, snapshot: Snapshot): File =
        getGenericImagePath(environment.goldenImagesFolder, snapshot)

internal fun getGenericImagePath(rootFolder: String, snapshot: Snapshot): File = File(rootFolder,
        "${snapshot.testName.packageName}.${snapshot.testName.className}/${snapshot.testName.methodName}/${snapshot.name.sanitizeForFilename()}.png"
).also { it.parentFile.mkdirs() }

fun buildMediaVerifier(environment: Environment, logger: ILogger): MediaVerifier {
    return when(environment.verifyMode) {
        VerifyMode.VerifyAgainstGolden -> PaparazziMediaVerifier(environment, logger)
        VerifyMode.GenerateToGolden -> PaparazziOverwritingMediaVerifier(environment, logger)
    }
}

internal class PaparazziMediaVerifier(private val environment: Environment, private val logger: ILogger) : MediaVerifier {

    override fun verify(snapshot: Snapshot, generatedImage: File) {
        val logPrefix = "Snapshot [${snapshot}]:"
        if (generatedImage.extension.equals("png", ignoreCase = true)) {
            val goldenImageFile = getGoldenImagePath(environment, snapshot)
            if (goldenImageFile.exists()) {
                //verifying
                val diffFile = getGenericImagePath("${environment.reportDir}/diff", snapshot)
                if (ImageUtils.areImagesSimilar(
                                ImageIO.read(goldenImageFile),
                                ImageIO.read(generatedImage),
                                0.1,/*hard code, sigh*/
                                diffFile
                        )) {
                    logger.info("$logPrefix Generated image ${generatedImage.absolutePath} matches golden-image ${goldenImageFile.absolutePath}. Diff (if minor) is at ${diffFile.absolutePath}.")
                } else {
                    val errorMessage = "Snapshot [${snapshot}]: Generated image ${generatedImage.absolutePath} DOES NOT match golden-image ${goldenImageFile.absolutePath}. Diff is at ${diffFile.absolutePath}."
                    logger.warning(errorMessage)
                    Assert.fail(errorMessage)
                }
            } else {
                val errorMessage = "Golden-image DOES NOT exist at ${goldenImageFile.absolutePath}."
                logger.warning(errorMessage)
                Assert.fail(errorMessage)
            }
        } else {
            logger.warning("Snapshot [${snapshot}]: Only supporting PNG snapshot verifications.")
        }
    }
}

class PaparazziOverwritingMediaVerifier(private val environment: Environment, private val logger: ILogger) : MediaVerifier {

    override fun verify(snapshot: Snapshot, generatedImage: File) {
        val goldenImageFile = getGoldenImagePath(environment, snapshot)
        //since we specify overwrite, we'll ignore the test (with assumption)
        //and copy the image over.
        generatedImage.copyTo(goldenImageFile, overwrite = true)
        logger.info("Snapshot [${snapshot}]: Golden image was overwritten. Copied the generated image to ${goldenImageFile.absolutePath}. You should verify that it is okay.")
    }
}