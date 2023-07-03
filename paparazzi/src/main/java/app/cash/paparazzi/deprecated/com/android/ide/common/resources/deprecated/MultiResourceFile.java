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

import app.cash.paparazzi.deprecated.com.android.ide.common.resources.deprecated.ValueResourceParser.IValueResourceRepository;
import app.cash.paparazzi.deprecated.com.android.io.IAbstractFile;
import app.cash.paparazzi.deprecated.com.android.io.StreamException;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.resources.ResourceType;
import com.android.utils.XmlUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public final class MultiResourceFile extends ResourceFile implements IValueResourceRepository {

    private static final SAXParserFactory sParserFactory = XmlUtils.configureSaxFactory(
            SAXParserFactory.newInstance(), false, false);

    private final Map<ResourceType, ResourceValueMap> mResourceItems =
        new EnumMap<ResourceType, ResourceValueMap>(ResourceType.class);

    private Collection<ResourceType> mResourceTypeList = null;

    public MultiResourceFile(IAbstractFile file, ResourceFolder folder) {
        super(file, folder);
    }

    // Boolean flag to track whether a named element has been added or removed, thus requiring
    // a new ID table to be generated
    private boolean mNeedIdRefresh;

    @Override
    protected void load(ScanningContext context) {
        // need to parse the file and find the content.
        parseFile();

        // create new ResourceItems for the new content.
        mResourceTypeList = Collections.unmodifiableCollection(mResourceItems.keySet());

        // We need an ID generation step
        mNeedIdRefresh = true;

        // create/update the resource items.
        updateResourceItems(context);
    }

    @Override
    protected void update(ScanningContext context) {
        // Reset the ID generation flag
        mNeedIdRefresh = false;

        // Copy the previous version of our list of ResourceItems and types
        Map<ResourceType, ResourceValueMap> oldResourceItems
                        = new EnumMap<ResourceType, ResourceValueMap>(mResourceItems);

        // reset current content.
        mResourceItems.clear();

        // need to parse the file and find the content.
        parseFile();

        // create new ResourceItems for the new content.
        mResourceTypeList = Collections.unmodifiableCollection(mResourceItems.keySet());

        // Check to see if any names have changed. If so, mark the flag so updateResourceItems
        // can notify the ResourceRepository that an ID refresh is needed
        if (oldResourceItems.keySet().equals(mResourceItems.keySet())) {
            for (ResourceType type : mResourceTypeList) {
                // We just need to check the names of the items.
                // If there are new or removed names then we'll have to regenerate IDs
                if (mResourceItems.get(type).keySet()
                                          .equals(oldResourceItems.get(type).keySet()) == false) {
                    mNeedIdRefresh = true;
                }
            }
        } else {
            // If our type list is different, obviously the names will be different
            mNeedIdRefresh = true;
        }
        // create/update the resource items.
        updateResourceItems(context);
    }

    @Override
    protected void dispose(ScanningContext context) {
        ResourceRepository repository = getRepository();

        // only remove this file from all existing ResourceItem.
        repository.removeFile(mResourceTypeList, this);

        // We'll need an ID refresh because we deleted items
        context.requestFullAapt();

        // don't need to touch the content, it'll get reclaimed as this objects disappear.
        // In the mean time other objects may need to access it.
    }

    @Override
    public Collection<ResourceType> getResourceTypes() {
        return mResourceTypeList;
    }

    @Override
    public boolean hasResources(ResourceType type) {
        ResourceValueMap list = mResourceItems.get(type);
        return (list != null && !list.isEmpty());
    }

    private void updateResourceItems(ScanningContext context) {
        ResourceRepository repository = getRepository();

        // remove this file from all existing ResourceItem.
        repository.removeFile(mResourceTypeList, this);

        for (ResourceType type : mResourceTypeList) {
            ResourceValueMap list = mResourceItems.get(type);

            if (list != null) {
                Collection<ResourceValue> values = list.values();
                for (ResourceValue res : values) {
                    ResourceItem item = repository.getResourceItem(type, res.getName());

                    // add this file to the list of files generating this resource item.
                    item.add(this);
                }
            }
        }

        // If we need an ID refresh, ask the repository for that now
        if (mNeedIdRefresh) {
            context.requestFullAapt();
        }
    }

    /**
     * Parses the file and creates a list of {@link ResourceType}.
     */
    private void parseFile() {
        try {
            SAXParser parser = XmlUtils.createSaxParser(sParserFactory);
            InputSource source = new InputSource(getFile().getContents());
            source.setEncoding(StandardCharsets.UTF_8.name());
            parser.parse(source, new ValueResourceParser(this, isFramework(), null));
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } catch (StreamException e) {
        }
    }

    /**
     * Adds a resource item to the list
     * @param value The value of the resource.
     */
    @Override
    public void addResourceValue(ResourceValue value) {
        ResourceType resType = value.getResourceType();

        ResourceValueMap list = mResourceItems.get(resType);

        // if the list does not exist, create it.
        if (list == null) {
            list = ResourceValueMap.create();
            mResourceItems.put(resType, list);
        } else {
            // look for a possible value already existing.
            ResourceValue oldValue = list.get(value.getName());

            if (oldValue instanceof ResourceValueImpl) {
                ((ResourceValueImpl) oldValue).replaceWith(value);
                return;
            }
        }

        // empty list or no match found? add the given resource
        list.put(value.getName(), value);
    }

    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        // get the list for the given type
        ResourceValueMap list = mResourceItems.get(type);

        if (list != null) {
            return list.get(name);
        }

        return null;
    }
}
