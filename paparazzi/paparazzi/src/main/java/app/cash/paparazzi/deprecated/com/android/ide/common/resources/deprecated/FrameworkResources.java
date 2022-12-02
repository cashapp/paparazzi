/*
 * Copyright (C) 2018 The Android Open Source Project
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

package app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated;

import app.cash.paparazzi.deprecated.com.android.io.IAbstractFile;
import app.cash.paparazzi.deprecated.com.android.io.IAbstractFolder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.utils.ILogger;
import com.google.common.base.Charsets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public class FrameworkResources extends ResourceRepository {

    /**
     * Map of {@link ResourceType} to list of items. It is guaranteed to contain a list for all
     * possible values of ResourceType.
     */
    protected final Map<ResourceType, List<ResourceItem>> mPublicResourceMap =
        new EnumMap<ResourceType, List<ResourceItem>>(ResourceType.class);

    public FrameworkResources(@NonNull IAbstractFolder resFolder) {
        super(resFolder, true /*isFrameworkRepository*/);
    }

    /**
     * Returns a {@link Collection} (always non null, but can be empty) of <b>public</b>
     * {@link ResourceItem} matching a given {@link ResourceType}.
     *
     * @param type the type of the resources to return
     * @return a collection of items, possibly empty.
     */
    @Override
    @NonNull
    public List<ResourceItem> getResourceItemsOfType(@NonNull ResourceType type) {
        return mPublicResourceMap.get(type);
    }

    /**
     * Returns whether the repository has <b>public</b> resources of a given {@link ResourceType}.
     * @param type the type of resource to check.
     * @return true if the repository contains resources of the given type, false otherwise.
     */
    @Override
    public boolean hasResourcesOfType(@NonNull ResourceType type) {
        return !mPublicResourceMap.get(type).isEmpty();
    }

    @Override
    @NonNull
    protected ResourceItem createResourceItem(@NonNull String name) {
        return new FrameworkResourceItem(name);
    }

    /**
     * Reads the public.xml file in data/res/values/ for a given resource folder and builds up
     * a map of public resources.
     *
     * This map is a subset of the full resource map that only contains framework resources
     * that are public.
     *
     * @param logger a logger to report issues to
     */
    public void loadPublicResources(@Nullable ILogger logger) {
        IAbstractFolder valueFolder = getResFolder().getFolder(SdkConstants.FD_RES_VALUES);
        if (!valueFolder.exists()) {
            return;
        }

        IAbstractFile publicXmlFile = valueFolder.getFile("public.xml"); //$NON-NLS-1$
        if (publicXmlFile.exists()) {
            Reader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(publicXmlFile.getContents(),
                        Charsets.UTF_8));
                KXmlParser parser = new KXmlParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(reader);

                ResourceType lastType = null;
                String lastTypeName = "";
                while (true) {
                    int event = parser.next();
                    if (event == XmlPullParser.START_TAG) {
                        // As of API 15 there are a number of "java-symbol" entries here
                        if (!parser.getName().equals("public")) { //$NON-NLS-1$
                            continue;
                        }

                        String name = null;
                        String typeName = null;
                        for (int i = 0, n = parser.getAttributeCount(); i < n; i++) {
                            String attribute = parser.getAttributeName(i);

                            if (attribute.equals("name")) { //$NON-NLS-1$
                                name = parser.getAttributeValue(i);
                                if (typeName != null) {
                                    // Skip id attribute processing
                                    break;
                                }
                            } else if (attribute.equals("type")) { //$NON-NLS-1$
                                typeName = parser.getAttributeValue(i);
                            }
                        }

                        if (name != null && typeName != null) {
                            ResourceType type = null;
                            if (typeName.equals(lastTypeName)) {
                                type = lastType;
                            } else {
                                type = ResourceType.fromXmlValue(typeName);
                                lastType = type;
                                lastTypeName = typeName;
                            }
                            if (type != null) {
                                ResourceItem match = null;
                                Map<String, ResourceItem> map = mResourceMap.get(type);
                                if (map != null) {
                                    match = map.get(name);
                                }

                                if (match != null) {
                                    List<ResourceItem> publicList = mPublicResourceMap.get(type);
                                    if (publicList == null) {
                                        // Pick initial size for the list to hold the public
                                        // resources. We could just use map.size() here,
                                        // but they're usually much bigger; for example,
                                        // in one platform version, there are 1500 drawables
                                        // and 1200 strings but only 175 and 25 public ones
                                        // respectively.
                                        int size;
                                        switch (type) {
                                            case STYLE: size = 500; break;
                                            case ATTR: size = 1050; break;
                                            case DRAWABLE: size = 200; break;
                                            case ID: size = 50; break;
                                            case LAYOUT:
                                            case COLOR:
                                            case STRING:
                                            case ANIM:
                                            case INTERPOLATOR:
                                                size = 30;
                                                break;
                                            default:
                                                size = 10;
                                                break;
                                        }
                                        publicList = new ArrayList<ResourceItem>(size);
                                        mPublicResourceMap.put(type, publicList);
                                    }

                                    publicList.add(match);
                                } else {
                                    // log that there's a public resource that doesn't actually
                                    // exist?
                                }
                            } else {
                                // log that there was a reference to a typo that doesn't actually
                                // exist?
                            }
                        }
                    } else if (event == XmlPullParser.END_DOCUMENT) {
                        break;
                    }
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.error(e, "Can't read and parse public attribute list");
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // Nothing to be done here - we don't care if it closed or not.
                    }
                }
            }
        }

        // put unmodifiable list for all res type in the public resource map
        // this will simplify access
        for (ResourceType type : ResourceType.values()) {
            List<ResourceItem> list = mPublicResourceMap.get(type);
            if (list == null) {
                list = Collections.emptyList();
            } else {
                list = Collections.unmodifiableList(list);
            }

            // put the new list in the map
            mPublicResourceMap.put(type, list);
        }
    }
}

