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
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.AssetRepository;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.deprecated.ResourceRepository;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableMap;
import com.squareup.paparazzi.PaparazziLogger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder to help setting up {@link SessionParams} objects.
 */
public class SessionParamsBuilder {

    private LayoutPullParser layoutPullParser;
    private RenderingMode renderingMode = RenderingMode.NORMAL;
    private Object projectKey = null;
    private DeviceConfig deviceConfig = DeviceConfig.NEXUS_5;
    private ResourceRepository frameworkResources;
    private ResourceRepository projectResources;
    private String themeName;
    private boolean isProjectTheme;
    private LayoutlibCallback layoutlibCallback;
    private int targetSdk;
    private int minSdk = 0;
    private PaparazziLogger logger;
    private Map<SessionParams.Key, Object> flags = new LinkedHashMap<>();
    private AssetRepository assetRepository = null;
    private boolean decor = true;

    @NonNull
    public SessionParamsBuilder setParser(@NonNull LayoutPullParser layoutParser) {
        this.layoutPullParser = layoutParser;

        return this;
    }

    @NonNull
    public SessionParamsBuilder setRenderingMode(@NonNull RenderingMode renderingMode) {
        this.renderingMode = renderingMode;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setDeviceConfig(@NonNull DeviceConfig deviceConfig) {
        this.deviceConfig = deviceConfig;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setProjectResources(@NonNull ResourceRepository resources) {
        projectResources = resources;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFrameworkResources(@NonNull ResourceRepository resources) {
        frameworkResources = resources;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTheme(@NonNull String themeName, boolean isProjectTheme) {
        this.themeName = themeName;
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
        layoutlibCallback = callback;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setTargetSdk(int targetSdk) {
        this.targetSdk = targetSdk;
        return this;
    }

    @SuppressWarnings("unused")
    @NonNull
    public SessionParamsBuilder setMinSdk(int minSdk) {
        this.minSdk = minSdk;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setLogger(@NonNull PaparazziLogger logger) {
        this.logger = logger;
        return this;
    }

    @NonNull
    public SessionParamsBuilder setFlag(@NonNull SessionParams.Key flag, Object value) {
        flags.put(flag, value);
        return this;
    }

    @NonNull
    public SessionParamsBuilder setAssetRepository(@NonNull AssetRepository repository) {
        assetRepository = repository;
        return this;
    }

    @NonNull
    public SessionParamsBuilder disableDecoration() {
        decor = false;
        return this;
    }

    @NonNull
    public SessionParams build() {
        assert frameworkResources != null;
        assert projectResources != null;
        assert themeName != null;
        assert logger != null;
        assert layoutlibCallback != null;

        FolderConfiguration config = deviceConfig.getFolderConfig();
        ResourceResolver resourceResolver = ResourceResolver.create(
                ImmutableMap.of(
                        ResourceNamespace.ANDROID, frameworkResources.getConfiguredResources(config),
                        ResourceNamespace.TODO(), projectResources.getConfiguredResources(config)),
                new ResourceReference(
                        ResourceNamespace.fromBoolean(!isProjectTheme),
                        ResourceType.STYLE,
                    themeName));

        SessionParams params = new SessionParams(layoutPullParser, renderingMode, projectKey /* for
        caching */, deviceConfig.getHardwareConfig(), resourceResolver, layoutlibCallback,
            minSdk, targetSdk, logger);

        flags.forEach(params::setFlag);
        params.setAssetRepository(assetRepository);

        if (!decor) {
            params.setForceNoDecor();
        }

        return params;
    }
}
