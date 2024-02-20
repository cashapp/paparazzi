package app.cash.paparazzi.snapshot.internal

import app.cash.paparazzi.snapshot.android.ide.common.resources.deprecated.ResourceRepository as LegacyResourceRepository
import com.android.ide.common.resources.ResourceRepository as NewResourceRepository

internal sealed interface ResourceRepositoryBridge {
  class Legacy(val repository: LegacyResourceRepository) : ResourceRepositoryBridge
  class New(val repository: NewResourceRepository) : ResourceRepositoryBridge
}
