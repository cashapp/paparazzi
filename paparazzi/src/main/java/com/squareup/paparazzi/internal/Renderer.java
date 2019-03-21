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
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.deprecated.FrameworkResources;
import com.android.ide.common.resources.deprecated.ResourceItem;
import com.android.ide.common.resources.deprecated.ResourceRepository;
import com.android.io.FolderWrapper;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.RenderParamsFlags;
import com.android.layoutlib.bridge.impl.DelegateManager;
import com.android.tools.layoutlib.java.System_Delegate;
import com.squareup.paparazzi.Environment;
import com.squareup.paparazzi.PaparazziLogger;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

/**
 * Base class for render tests. The render tests load all the framework resources and a project
 * checked in this test's resources. The main dependencies
 * are:
 * 1. Fonts directory.
 * 2. Framework Resources.
 * 3. App resources.
 * 4. build.prop file
 * <p>
 * These are configured by two variables set in the system properties.
 * <p>
 * 1. platform.dir: This is the directory for the current platform in the built SDK
 * (.../sdk/platforms/android-<version>).
 * <p>
 * The fonts are platform.dir/data/fonts.
 * The Framework resources are platform.dir/data/res.
 * build.prop is at platform.dir/build.prop.
 * <p>
 * 2. test_res.dir: This is the directory for the resources of the test. If not specified, this
 * falls back to getClass().getProtectionDomain().getCodeSource().getLocation()
 * <p>
 * The app resources are at: test_res.dir/testApp/MyApplication/app/src/main/res
 */
public class Renderer implements Closeable {
    private Bridge bridge;
    private FrameworkResources frameworkRepo;
    private ResourceRepository projectResources;
    private final PaparazziLogger logger;
    private final Environment environment;
    private final PaparazziLayoutLibCallback layoutLibCallback;

    public Renderer(
        Environment environment,
        PaparazziLayoutLibCallback layoutLibCallback,
        PaparazziLogger logger) {
        this.environment = environment;
        this.layoutLibCallback = layoutLibCallback;
        this.logger = logger;
    }

    /**
     * Initialize the bridge and the resource maps.
     */
    public void prepare() {
        File data_dir = new File(environment.getPlatformDir(), "data");
        File res = new File(data_dir, "res");
        frameworkRepo = new FrameworkResources(new FolderWrapper(res));
        frameworkRepo.loadResources();
        frameworkRepo.loadPublicResources(logger);

        projectResources =
                new ResourceRepository(new FolderWrapper(environment.getResDir()),
                        false) {
                    @NonNull
                    @Override
                    protected ResourceItem createResourceItem(@NonNull String name) {
                        return new ResourceItem(name);
                    }
                };
        projectResources.loadResources();

        File fontLocation = new File(data_dir, "fonts");
        File buildProp = new File(environment.getPlatformDir(), "build.prop");
        File attrs = new File(res, "values" + File.separator + "attrs.xml");
        bridge = new Bridge();
        bridge.init(DeviceConfig.loadProperties(buildProp), fontLocation,
                DeviceConfig.getEnumMap(attrs), logger);
        Bridge.getLock().lock();
        try {
            Bridge.setLog(logger);
        } finally {
            Bridge.getLock().unlock();
        }
    }

    @Override public void close() {
        frameworkRepo = null;
        projectResources = null;
        bridge = null;

        Gc.gc();

        System.out.println("Objects still linked from the DelegateManager:");
        DelegateManager.dump(System.out);
    }

    @NonNull
    protected RenderResult render(com.android.ide.common.rendering.api.Bridge bridge,
            SessionParams params, long frameTimeNanos) {
        // TODO: Set up action bar handler properly to test menu rendering.
        // Create session params.
        System_Delegate.setBootTimeNanos(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        System_Delegate.setNanosTime(TimeUnit.MILLISECONDS.toNanos(871732800000L));
        RenderSession session = bridge.createSession(params);

        try {
            if (frameTimeNanos != -1) {
                session.setElapsedFrameTimeNanos(frameTimeNanos);
            }

            if (!session.getResult().isSuccess()) {
                logger.error(session.getResult().getException(),
                        session.getResult().getErrorMessage());
            }
            else {
                // Render the session with a timeout of 50s.
                Result renderResult = session.render(50000);
                if (!renderResult.isSuccess()) {
                    logger.error(session.getResult().getException(),
                            session.getResult().getErrorMessage());
                }
            }

            return RenderResult.getFromSession(session);
        } finally {
            session.dispose();
        }
    }

    /**
     * Compares the golden image with the passed image
     */
    protected void verify(@NonNull String goldenImageName, @NonNull BufferedImage image) {
        try {
            String goldenImagePath = environment.getAppTestDir() + "/golden/" + goldenImageName;
            ImageUtils.requireSimilar(goldenImagePath, image);
        } catch (IOException e) {
            logger.error(e, e.getMessage());
        }
    }

    /**
     * Create a new rendering session and test that rendering the given layout doesn't throw any
     * exceptions and matches the provided image.
     * <p>
     * If frameTimeNanos is >= 0 a frame will be executed during the rendering. The time indicates
     * how far in the future is.
     */
    @Nullable
    protected RenderResult renderAndVerify(
        SessionParams params, String goldenFileName, long frameTimeNanos) {
        RenderResult result = render(bridge, params, frameTimeNanos);
        assertNotNull(result.getImage());
        verify(goldenFileName, result.getImage());

        return result;
    }

    /**
     * Create a new rendering session and test that rendering the given layout doesn't throw any
     * exceptions and matches the provided image.
     */
    @Nullable
    protected RenderResult renderAndVerify(SessionParams params, String goldenFileName) {
        return renderAndVerify(params, goldenFileName, -1);
    }

    @NonNull
    protected LayoutPullParser createParserFromPath(String layoutPath) {
        return LayoutPullParser.createFromPath(environment.getResDir() + "/layout/" + layoutPath);
    }

    /**
     * Create a new rendering session and test that rendering the given layout on nexus 5
     * doesn't throw any exceptions and matches the provided image.
     */
    @Nullable
    protected RenderResult renderAndVerify(String layoutFileName, String goldenFileName) {
        return renderAndVerify(layoutFileName, goldenFileName, DeviceConfig.NEXUS_5);
    }

    /**
     * Create a new rendering session and test that rendering the given layout on given device
     * doesn't throw any exceptions and matches the provided image.
     */
    @Nullable
    protected RenderResult renderAndVerify(String layoutFileName, String goldenFileName,
            DeviceConfig deviceConfig) {
        SessionParams params = createSessionParams(layoutFileName, deviceConfig);
        return renderAndVerify(params, goldenFileName);
    }

    protected SessionParams createSessionParams(
        String layoutFileName, DeviceConfig deviceConfig) {
        // Create the layout pull parser.
        LayoutPullParser parser = createParserFromPath(layoutFileName);
        // TODO: Set up action bar handler properly to test menu rendering.
        // Create session params.
        return getSessionParamsBuilder()
                .setParser(parser)
                .setDeviceConfig(deviceConfig)
                .setCallback(layoutLibCallback)
                .build();
    }

    /**
     * Returns a pre-configured {@link SessionParamsBuilder} for target API 22, Normal rendering
     * mode, AppTheme as theme and Nexus 5.
     */
    @NonNull
    public SessionParamsBuilder getSessionParamsBuilder() {
        return new SessionParamsBuilder()
                .setLogger(logger)
                .setFrameworkResources(frameworkRepo)
                .setDeviceConfig(DeviceConfig.NEXUS_5)
                .setProjectResources(projectResources)
                .setTheme("AppTheme", true)
                .setRenderingMode(RenderingMode.NORMAL)
                .setTargetSdk(22)
                .setFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
                .setAssetRepository(new PaparazziAssetRepository(environment.getTestResDir() + "/" + environment.getAssetsDir()));
    }
}
