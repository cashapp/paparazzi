/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.annotation.Nullable;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderResult {
    private final List<ViewInfo> rootViews;
    private final List<ViewInfo> systemViews;
    private final Result renderResult;
    private BufferedImage image;

    private RenderResult(@Nullable Result result, @Nullable List<ViewInfo> systemViewInfoList,
            @Nullable List<ViewInfo> rootViewInfoList, @Nullable BufferedImage image) {
        this.systemViews = systemViewInfoList == null ? Collections.emptyList() : systemViewInfoList;
        this.rootViews = rootViewInfoList == null ? Collections.emptyList() : rootViewInfoList;
        this.renderResult = result;
        this.image = image;
    }

    @NonNull
    public static RenderResult getFromSession(@NonNull RenderSession session) {
        return new RenderResult(session.getResult(),
                new ArrayList<>(session.getSystemRootViews()),
                new ArrayList<>(session.getRootViews()),
                session.getImage());
    }

    @Nullable
    public Result getResult() {
        return renderResult;
    }

    @NonNull
    public List<ViewInfo> getRootViews() {
        return rootViews;
    }

    @NonNull
    public List<ViewInfo> getSystemViews() {
        return systemViews;
    }

    @Nullable
    public BufferedImage getImage() {
        return image;
    }
}
