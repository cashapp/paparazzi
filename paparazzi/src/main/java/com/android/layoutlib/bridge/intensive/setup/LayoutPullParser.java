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

package com.android.layoutlib.bridge.intensive.setup;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.ATTR_IGNORE;
import static com.android.SdkConstants.EXPANDABLE_LIST_VIEW;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.LIST_VIEW;
import static com.android.SdkConstants.SPINNER;
import static com.android.SdkConstants.TOOLS_URI;

public class LayoutPullParser extends KXmlParser implements ILayoutPullParser{

    private ResourceNamespace mLayoutNamespace = ResourceNamespace.RES_AUTO;

    @NonNull
    public static LayoutPullParser createFromFile(@NonNull File layoutFile)
            throws FileNotFoundException {
        return new LayoutPullParser(new FileInputStream(layoutFile));
    }

    /**
     * @param layoutPath Must start with '/' and be relative to test resources.
     */
    @NonNull
    public static LayoutPullParser createFromPath(@NonNull String layoutPath) {
        if (layoutPath.startsWith("/")) {
            layoutPath = layoutPath.substring(1);
        }

        return new LayoutPullParser(LayoutPullParser.class.getClassLoader().getResourceAsStream
                (layoutPath));
    }

    @NonNull
    public static LayoutPullParser createFromString(@NonNull String contents) {
        return new LayoutPullParser(new ByteArrayInputStream(
                contents.getBytes(Charset.forName("UTF-8"))));
    }

    private LayoutPullParser(@NonNull InputStream inputStream) {
        try {
            setFeature(FEATURE_PROCESS_NAMESPACES, true);
            setInput(inputStream, null);
        } catch (XmlPullParserException e) {
            throw new IOError(e);
        }
    }

    @Override
    public Object getViewCookie() {
        // TODO: Implement this properly.
        String name = super.getName();
        if (name == null) {
            return null;
        }

        // Store tools attributes if this looks like a layout we'll need adapter view
        // bindings for in the LayoutlibCallback.
        if (LIST_VIEW.equals(name) || EXPANDABLE_LIST_VIEW.equals(name) || GRID_VIEW.equals(name) || SPINNER.equals(name)) {
            Map<String, String> map = null;
            int count = getAttributeCount();
            for (int i = 0; i < count; i++) {
                String namespace = getAttributeNamespace(i);
                if (namespace != null && namespace.equals(TOOLS_URI)) {
                    String attribute = getAttributeName(i);
                    if (attribute.equals(ATTR_IGNORE)) {
                        continue;
                    }
                    if (map == null) {
                        map = new HashMap<>(4);
                    }
                    map.put(attribute, getAttributeValue(i));
                }
            }

            return map;
        }

        return null;
    }

    @Override
    @NonNull
    public ResourceNamespace getLayoutNamespace() {
        return mLayoutNamespace;
    }

    public void setLayoutNamespace(@NonNull ResourceNamespace layoutNamespace) {
        mLayoutNamespace = layoutNamespace;
    }
}
