/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.paparazzi.internal;

import android.annotation.NonNull;
import com.android.ide.common.rendering.api.AssetRepository;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@link AssetRepository} used for render tests.
 */
public class PaparazziAssetRepository extends AssetRepository {
    private final String assetPath;

    public PaparazziAssetRepository(@NonNull String assetPath) {
        this.assetPath = assetPath;
    }

    private static InputStream open(String path) throws FileNotFoundException {
        File asset = new File(path);
        if (asset.isFile()) {
            return new FileInputStream(asset);
        }

        return null;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public InputStream openAsset(String path, int mode) throws IOException {
        return open(assetPath + path);
    }

    @Override
    public InputStream openNonAsset(int cookie, String path, int mode) throws IOException {
        return open(path);
    }
}
