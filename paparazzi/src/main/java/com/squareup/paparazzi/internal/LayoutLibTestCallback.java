/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.ide.common.rendering.api.AdapterBinding;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SessionParams.Key;
import com.android.layoutlib.bridge.android.RenderParamsFlags;
import com.android.resources.ResourceType;
import com.android.utils.ILogger;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;

public class LayoutLibTestCallback extends LayoutlibCallback {
    private static final String PACKAGE_NAME = "com.squareup.paparazzi.sample";

    private final Map<Integer, ResourceReference> mProjectResources = new HashMap<>();
    private final Map<ResourceReference, Integer> mResources = new HashMap<>();
    private final ILogger mLog;
    private final ActionBarCallback mActionBarCallback = new ActionBarCallback();
    private final ClassLoader mModuleClassLoader;
    private String mAdaptiveIconMaskPath;

    public LayoutLibTestCallback(ILogger logger, ClassLoader classLoader) {
        mLog = logger;
        mModuleClassLoader = classLoader;
    }

    public void initResources() throws ClassNotFoundException {
        Class<?> rClass = mModuleClassLoader.loadClass(PACKAGE_NAME + ".R");
        Class<?>[] nestedClasses = rClass.getDeclaredClasses();
        for (Class<?> resClass : nestedClasses) {
            final ResourceType resType = ResourceType.getEnum(resClass.getSimpleName());

            if (resType != null) {
                for (Field field : resClass.getDeclaredFields()) {
                    final int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers)) { // May not be final in library projects
                        final Class<?> type = field.getType();
                        try {
                            if (type == int.class) {
                                final Integer value = (Integer) field.get(null);
                                ResourceReference reference =
                                        new ResourceReference(RES_AUTO, resType, field.getName());
                                mProjectResources.put(value, reference);
                                mResources.put(reference, value);
                            } else if (!(type.isArray() && type.getComponentType() == int.class)) {
                                mLog.error(null, "Unknown field type in R class: %1$s", type);
                            }
                        } catch (IllegalAccessException e) {
                            mLog.error(e, "Malformed R class: %1$s", PACKAGE_NAME + ".R");
                        }
                    }
                }
            }
        }
    }


    @Override
    public Object loadView(@NonNull String name, @NonNull Class[] constructorSignature, Object[] constructorArgs)
            throws Exception {
        Class<?> viewClass = mModuleClassLoader.loadClass(name);
        Constructor<?> viewConstructor = viewClass.getConstructor(constructorSignature);
        viewConstructor.setAccessible(true);
        return viewConstructor.newInstance(constructorArgs);
    }

    @NonNull
    @Override
    public String getNamespace() {
        return String.format(SdkConstants.NS_CUSTOM_RESOURCES_S, PACKAGE_NAME);
    }

    @Override
    public ResourceReference resolveResourceId(int id) {
        return mProjectResources.get(id);
    }

    @Override
    public int getOrGenerateResourceId(@NonNull ResourceReference resource) {
        Integer id = mResources.get(resource);
        return id != null ? id : 0;
    }

    @Override
    public ILayoutPullParser getParser(@NonNull ResourceValue layoutResource) {
        try {
            return LayoutPullParser.createFromFile(new File(layoutResource.getValue()));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public Object getAdapterItemValue(ResourceReference adapterView, Object adapterCookie,
            ResourceReference itemRef, int fullPosition, int positionPerType,
            int fullParentPosition, int parentPositionPerType, ResourceReference viewRef,
            ViewAttribute viewAttribute, Object defaultValue) {
        return null;
    }

    @Override
    public AdapterBinding getAdapterBinding(ResourceReference adapterViewRef, Object adapterCookie,
            Object viewObject) {
        return null;
    }

    @Override
    public ActionBarCallback getActionBarCallback() {
        return mActionBarCallback;
    }

    @Override
    public boolean supports(int ideFeature) {
        return false;
    }

    @Override
    @Nullable
    public XmlPullParser createXmlParserForPsiFile(@NonNull String fileName) {
        return createXmlParserForFile(fileName);
    }

    @Override
    @Nullable
    public XmlPullParser createXmlParserForFile(@NonNull String fileName) {
        try (FileInputStream fileStream = new FileInputStream(fileName)) {
            // Read data fully to memory to be able to close the file stream.
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            ByteStreams.copy(fileStream, byteOutputStream);
            KXmlParser parser = new KXmlParser();
            parser.setInput(new ByteArrayInputStream(byteOutputStream.toByteArray()), null);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            return parser;
        } catch (IOException | XmlPullParserException e) {
            return null;
        }
    }

    @Override
    @NonNull
    public XmlPullParser createXmlParser() {
        return new KXmlParser();
    }

    @Override
    @SuppressWarnings("unchecked") // The Key<T> API is based on unchecked casts.
    public <T> T getFlag(Key<T> key) {
        if (key.equals(RenderParamsFlags.FLAG_KEY_APPLICATION_PACKAGE)) {
            return (T) PACKAGE_NAME;
        }
        if (key.equals(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH)) {
            return (T) mAdaptiveIconMaskPath;
        }
        return null;
    }

    public void setAdaptiveIconMaskPath(String adaptiveIconMaskPath) {
        mAdaptiveIconMaskPath = adaptiveIconMaskPath;
    }
}
