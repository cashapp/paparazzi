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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public class ResourceItem implements Comparable<ResourceItem> {

    private static final Comparator<ResourceFile> sComparator = new Comparator<ResourceFile>() {
        @Override
        public int compare(ResourceFile file1, ResourceFile file2) {
            // get both FolderConfiguration and compare them
            FolderConfiguration fc1 = file1.getFolder().getConfiguration();
            FolderConfiguration fc2 = file2.getFolder().getConfiguration();

            return fc1.compareTo(fc2);
        }
    };

    private final String mName;

    /**
     * List of files generating this ResourceItem.
     */
    private final List<ResourceFile> mFiles = new ArrayList<ResourceFile>();

    /**
     * Constructs a new ResourceItem.
     * @param name the name of the resource as it appears in the XML and R.java files.
     */
    public ResourceItem(String name) {
        mName = name;
    }

    /**
     * Returns the name of the resource.
     */
    public final String getName() {
        return mName;
    }

    /**
     * Compares the {@link ResourceItem} to another.
     * @param other the ResourceItem to be compared to.
     */
    @Override
    public int compareTo(ResourceItem other) {
        return mName.compareTo(other.mName);
    }

    /**
     * Returns whether the resource is editable directly.
     * <p>
     * This is typically the case for resources that don't have alternate versions, or resources
     * of type {@link ResourceType#ID} that aren't declared inline.
     */
    public boolean isEditableDirectly() {
        return hasAlternates() == false;
    }

    /**
     * Returns whether the ID resource has been declared inline inside another resource XML file.
     * If the resource type is not {@link ResourceType#ID}, this will always return {@code false}.
     */
    public boolean isDeclaredInline() {
        return false;
    }

    /**
     * Returns a {@link ResourceValue} for this item based on the given configuration.
     * If the ResourceItem has several source files, one will be selected based on the config.
     * @param type the type of the resource. This is necessary because ResourceItem doesn't embed
     *     its type, but ResourceValue does.
     * @param referenceConfig the config of the resource item.
     * @param isFramework whether the resource is a framework value. Same as the type.
     * @return a ResourceValue or null if none match the config.
     */
    public ResourceValue getResourceValue(ResourceType type, FolderConfiguration referenceConfig,
            boolean isFramework) {
        // look for the best match for the given configuration
        // the match has to be of type ResourceFile since that's what the input list contains
        ResourceFile match = (ResourceFile) referenceConfig.findMatchingConfigurable(mFiles);

        if (match != null) {
            // get the value of this configured resource.
            return match.getValue(type, mName);
        }

        return null;
    }

    /**
     * Adds a new source file.
     * @param file the source file.
     */
    protected void add(ResourceFile file) {
        mFiles.add(file);
    }

    /**
     * Removes a file from the list of source files.
     * @param file the file to remove
     */
    protected void removeFile(ResourceFile file) {
        mFiles.remove(file);
    }

    /**
     * Returns {@code true} if the item has no source file.
     * @return true if the item has no source file.
     */
    protected boolean hasNoSourceFile() {
        return mFiles.isEmpty();
    }

    /**
     * Reset the item by emptying its source file list.
     */
    protected void reset() {
        mFiles.clear();
    }

    /**
     * Returns the sorted list of {@link ResourceItem} objects for this resource item.
     */
    public ResourceFile[] getSourceFileArray() {
        ArrayList<ResourceFile> list = new ArrayList<ResourceFile>();
        list.addAll(mFiles);

        Collections.sort(list, sComparator);

        return list.toArray(new ResourceFile[0]);
    }

    /**
     * Returns the list of source file for this resource.
     */
    public List<ResourceFile> getSourceFileList() {
        return Collections.unmodifiableList(mFiles);
    }

    /**
     * Returns if the resource has at least one non-default version.
     *
     * @see ResourceFile#getConfiguration()
     * @see FolderConfiguration#isDefault()
     */
    public boolean hasAlternates() {
        for (ResourceFile file : mFiles) {
            if (file.getFolder().getConfiguration().isDefault() == false) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the resource has a default version, with no qualifier.
     *
     * @see ResourceFile#getConfiguration()
     * @see FolderConfiguration#isDefault()
     */
    public boolean hasDefault() {
        for (ResourceFile file : mFiles) {
            if (file.getFolder().getConfiguration().isDefault()) {
                return true;
            }
        }

        // We only want to return false if there's no default and more than 0 items.
        return (mFiles.isEmpty());
    }

    /**
     * Returns the number of alternate versions for this resource.
     *
     * @see ResourceFile#getConfiguration()
     * @see FolderConfiguration#isDefault()
     */
    public int getAlternateCount() {
        int count = 0;
        for (ResourceFile file : mFiles) {
            if (file.getFolder().getConfiguration().isDefault() == false) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns a formatted string usable in an XML to use for the {@link ResourceItem}.
     * @param system Whether this is a system resource or a project resource.
     * @return a string in the format @[type]/[name]
     */
    public String getXmlString(ResourceType type, boolean system) {
        if (type == ResourceType.ID && isDeclaredInline()) {
            return (system ? "@android:" : "@+") + type.getName() + "/" + mName; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        return (system ? "@android:" : "@") + type.getName() + "/" + mName; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Override
    public String toString() {
        return "ResourceItem [mName=" + mName + ", mFiles=" + mFiles + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
