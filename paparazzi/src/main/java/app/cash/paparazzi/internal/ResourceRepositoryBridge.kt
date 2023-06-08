package app.cash.paparazzi.internal

import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.ResourceRepository as LegacyResourceRepository
import com.android.ide.common.resources.ResourceRepository as NewResourceRepository

sealed interface ResourceRepositoryBridge {
  class Legacy(val repository: LegacyResourceRepository) : ResourceRepositoryBridge
  class New(val repository: NewResourceRepository) : ResourceRepositoryBridge
}
