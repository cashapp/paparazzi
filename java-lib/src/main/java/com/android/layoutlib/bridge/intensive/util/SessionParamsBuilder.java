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

package com.android.layoutlib.bridge.intensive.util;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.deprecated.ResourceRepository;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.resources.ResourceType;

import android.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Builder to help setting up {@link SessionParams} objects.
 */
public class SessionParamsBuilder {

    private LayoutPullParser mLayoutParser;
    private RenderingMode mRenderingMode = RenderingMode.NORMAL;
    private Object mProjectKey = null;
    private ConfigGenerator mConfigGenerator = ConfigGenerator.NEXUS_5;
    private ResourceRepository mFrameworkResources;
    private ResourceRepository mProjectResources;
    private String mThemeName;
    private boolean isProjectTheme;
    private LayoutlibCallback mLayoutlibCallback;
    private int mTargetSdk;
    private int mMinSdk = 0;
    private LayoutLog mLayoutLog;
    private Map<SessionParams.Key, Object> mFlags = new HashMap<>();
    private AssetRepository mAssetRepository = null;
    private boolean mDecor = true;

    @NonNull
    public SessionParamsBuilder setParser(@NonNull LayoutPullParser layoutParser) {
        mLayoutParser = layoutParser;

        return this;
    }

    @NonNull
    public SessionParamsBuilder setRenderingMode(@NonNull RenderingMode renderingMode) {
        mRenderingMode = renderingMode;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setConfigGenerator(@NonNull ConfigGenerator configGenerator) {
        mConfigGenerator = configGenerator;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setProjectResources(@NonNull ResourceRepository resources) {
        mProjectResources = resources;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFrameworkResources(@NonNull ResourceRepository resources) {
        mFrameworkResources = resources;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTheme(@NonNull String themeName, boolean isProjectTheme) {
        mThemeName = themeName;
        this.isProjectTheme = isProjectTheme;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTheme(@NonNull String themeName) {
        boolean isProjectTheme;
        if (themeName.startsWith(SdkConstants.PREFIX_ANDROID)) {
            themeName = themeName.substring(SdkConstants.PREFIX_ANDROID.length());
            isProjectTheme = false;
        } else {
            isProjectTheme = true;
        }
        return setTheme(themeName, isProjectTheme);
    }

    @NonNull
    public SessionParamsBuilder setCallback(@NonNull LayoutlibCallback callback) {
        mLayoutlibCallback = callback;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTargetSdk(int targetSdk) {
        mTargetSdk = targetSdk;
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    public SessionParamsBuilder setMinSdk(int minSdk) {
        mMinSdk = minSdk;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setLayoutLog(@NonNull LayoutLog layoutLog) {
        mLayoutLog = layoutLog;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFlag(@NonNull SessionParams.Key flag, Object value) {
        mFlags.put(flag, value);
        return this;
    }

    @NonNull
    public SessionParamsBuilder setAssetRepository(@NonNull AssetRepository repository) {
        mAssetRepository = repository;
        return this;
    }

    @NonNull
    public SessionParamsBuilder disableDecoration() {
        mDecor = false;
        return this;
    }

    @NonNull
    public SessionParams build() {
        assert mFrameworkResources != null;
        assert mProjectResources != null;
        assert mThemeName != null;
        assert mLayoutLog != null;
        assert mLayoutlibCallback != null;

        FolderConfiguration config = mConfigGenerator.getFolderConfig();
        ResourceResolver resourceResolver = ResourceResolver.create(
                ImmutableMap.of(
                        ResourceNamespace.ANDROID, mFrameworkResources.getConfiguredResources(config),
                        ResourceNamespace.TODO(), mProjectResources.getConfiguredResources(config)),
                new ResourceReference(
                        ResourceNamespace.fromBoolean(!isProjectTheme),
                        ResourceType.STYLE,
                        mThemeName));

        SessionParams params = new SessionParams(mLayoutParser, mRenderingMode, mProjectKey /* for
        caching */, mConfigGenerator.getHardwareConfig(), resourceResolver, mLayoutlibCallback,
                mMinSdk, mTargetSdk, mLayoutLog);

        mFlags.forEach(params::setFlag);
        params.setAssetRepository(mAssetRepository);

        if (!mDecor) {
            params.setForceNoDecor();
        }

        return params;
    }
}
