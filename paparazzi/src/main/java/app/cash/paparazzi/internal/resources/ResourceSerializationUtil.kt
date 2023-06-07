/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.internal.resources

import app.cash.paparazzi.internal.resources.base.BasicResourceItem
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.utils.Base128InputStream
import com.android.utils.Base128InputStream.StreamFormatException
import java.io.IOException
import java.util.function.Consumer
import java.util.function.Function

/**
 * Ported from: [ResourceSerializationUtil.java](https://cs.android.com/android-studio/platform/tools/base/+/18047faf69512736b8ddb1f6a6785f58d47c893f:resource-repository/main/java/com/android/resources/base/ResourceSerializationUtil.java)
 */
object ResourceSerializationUtil {
  /**
   * Loads resources from the given input stream and passes them to the given consumer.
   */
  @Throws(IOException::class)
  fun readResourcesFromStream(
    stream: Base128InputStream,
    stringCache: Map<String, String>,
    namespaceResolverCache: MutableMap<NamespaceResolver, NamespaceResolver>?,
    repository: LoadableResourceRepository,
    resourceConsumer: Consumer<BasicResourceItem>
  ) {
    // Enable string instance sharing to minimize memory consumption.
    stream.setStringCache(stringCache)

    var n = stream.readInt()
    if (n == 0) {
      return // Nothing to load.
    }
    val configurations = (0 until n).map {
      val configQualifier = stream.readString() ?: throw StreamFormatException.invalidFormat()
      val folderConfig = FolderConfiguration.getConfigForQualifierString(configQualifier)
        ?: throw StreamFormatException.invalidFormat()
      RepositoryConfiguration(repository, folderConfig)
    }

    n = stream.readInt()
    val newSourceFiles = (0 until n).map {
      repository.deserializeResourceSourceFile(stream, configurations)
    }

    n = stream.readInt()
    val newNamespaceResolvers = (0 until n).map {
      var namespaceResolver = NamespaceResolver.deserialize(stream)
      if (namespaceResolverCache != null) {
        namespaceResolver =
          namespaceResolverCache.computeIfAbsent(namespaceResolver, Function.identity())
      }
      namespaceResolver
    }

    n = stream.readInt()
    (0 until n).forEach { _ ->
      val item = BasicResourceItem.deserialize(
        stream,
        configurations,
        newSourceFiles,
        newNamespaceResolvers
      )
      resourceConsumer.accept(item)
    }
  }
}
