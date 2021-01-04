package app.cash.paparazzi.internal

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.android.ide.common.rendering.api.AssetRepository
import java.io.InputStream

internal class PaparazziContext(base: Context, private val assetRepository: AssetRepository)
    : ContextWrapper(base) {

  override fun getAssets(): AssetManager = PaparazziAssetManager(assetRepository)

}

internal class PaparazziAssetManager(private val assetRepository: AssetRepository)
    : AssetManager() {

  override fun open(fileName: String): InputStream = open(fileName, ACCESS_STREAMING)

  override fun open(fileName: String, mode: Int): InputStream =
    assetRepository.openAsset("/$fileName", mode)

}
