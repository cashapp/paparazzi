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
import app.cash.paparazzi.deprecated.com.android.io.IAbstractResource;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.android.SdkConstants.ATTR_REF_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.RESOURCE_CLZ_ATTR;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public abstract class ResourceRepository {
    private final IAbstractFolder mResourceFolder;

    protected Map<ResourceFolderType, List<ResourceFolder>> mFolderMap =
            new EnumMap<>(ResourceFolderType.class);

    protected Map<ResourceType, Map<String, ResourceItem>> mResourceMap =
            new EnumMap<>(ResourceType.class);

    private Map<Map<String, ResourceItem>, Collection<ResourceItem>> mReadOnlyListMap =
            new IdentityHashMap<>();

    private final boolean mFrameworkRepository;
    private boolean mCleared = true;
    private boolean mInitializing;

    /**
     * Makes a resource repository.
     *
     * @param resFolder the resource folder of the repository.
     * @param isFrameworkRepository whether the repository is for framework resources.
     */
    protected ResourceRepository(@NonNull IAbstractFolder resFolder,
            boolean isFrameworkRepository) {
        mResourceFolder = resFolder;
        mFrameworkRepository = isFrameworkRepository;
    }

    public IAbstractFolder getResFolder() {
        return mResourceFolder;
    }

    public boolean isFrameworkRepository() {
        return mFrameworkRepository;
    }

    public synchronized void clear() {
        mCleared = true;
        mFolderMap = new EnumMap<ResourceFolderType, List<ResourceFolder>>(
                ResourceFolderType.class);
        mResourceMap = new EnumMap<ResourceType, Map<String, ResourceItem>>(
                ResourceType.class);

        mReadOnlyListMap =
            new IdentityHashMap<Map<String, ResourceItem>, Collection<ResourceItem>>();
    }

    /**
     * Ensures that the repository has been initialized again after a call to
     * {@link ResourceRepository#clear()}.
     *
     * @return true if the repository was just re-initialized.
     */
    public synchronized boolean ensureInitialized() {
        if (mCleared && !mInitializing) {
            ScanningContext context = new ScanningContext();
            mInitializing = true;

            IAbstractResource[] resources = mResourceFolder.listMembers();

            for (IAbstractResource res : resources) {
                if (res instanceof IAbstractFolder) {
                    IAbstractFolder folder = (IAbstractFolder)res;
                    ResourceFolder resFolder = processFolder(folder);

                    if (resFolder != null) {
                        // now we process the content of the folder
                        IAbstractResource[] files = folder.listMembers();

                        for (IAbstractResource fileRes : files) {
                            if (fileRes instanceof IAbstractFile) {
                                IAbstractFile file = (IAbstractFile)fileRes;

                                resFolder.processFile(file, ResourceDeltaKind.ADDED, context);
                            }
                        }
                    }
                }
            }

            mInitializing = false;
            mCleared = false;
            return true;
        }

        return false;
    }

    /**
     * Adds a Folder Configuration to the project.
     *
     * @param type The resource type.
     * @param config The resource configuration.
     * @param folder The workspace folder object.
     * @return the {@link ResourceFolder} object associated to this folder.
     */
    private ResourceFolder add(
            @NonNull ResourceFolderType type,
            @NonNull FolderConfiguration config,
            @NonNull IAbstractFolder folder) {
        // get the list for the resource type
        List<ResourceFolder> list = mFolderMap.get(type);

        if (list == null) {
            list = new ArrayList<ResourceFolder>();

            ResourceFolder cf = new ResourceFolder(type, config, folder, this);
            list.add(cf);

            mFolderMap.put(type, list);

            return cf;
        }

        // look for an already existing folder configuration.
        for (ResourceFolder cFolder : list) {
            if (cFolder.mConfiguration.equals(config)) {
                // config already exist. Nothing to be done really, besides making sure
                // the IAbstractFolder object is up to date.
                cFolder.mFolder = folder;
                return cFolder;
            }
        }

        // If we arrive here, this means we didn't find a matching configuration.
        // So we add one.
        ResourceFolder cf = new ResourceFolder(type, config, folder, this);
        list.add(cf);

        return cf;
    }

    /**
     * Removes a {@link ResourceFolder} associated with the specified {@link IAbstractFolder}.
     *
     * @param type The type of the folder
     * @param removedFolder the IAbstractFolder object.
     * @param context the scanning context
     * @return the {@link ResourceFolder} that was removed, or null if no matches were found.
     */
    @Nullable
    public ResourceFolder removeFolder(
            @NonNull ResourceFolderType type,
            @NonNull IAbstractFolder removedFolder,
            @Nullable ScanningContext context) {
        ensureInitialized();

        // get the list of folders for the resource type.
        List<ResourceFolder> list = mFolderMap.get(type);

        if (list != null) {
            int count = list.size();
            for (int i = 0 ; i < count ; i++) {
                ResourceFolder resFolder = list.get(i);
                IAbstractFolder folder = resFolder.getFolder();
                if (removedFolder.equals(folder)) {
                    // we found the matching ResourceFolder. we need to remove it.
                    list.remove(i);

                    // remove its content
                    resFolder.dispose(context);

                    return resFolder;
                }
            }
        }

        return null;
    }

    /**
     * Returns true if this resource repository contains a resource of the given name.
     *
     * @param url the resource URL
     * @return true if the resource is known
     */
    public boolean hasResourceItem(@NonNull String url) {
        // Handle theme references
        if (url.startsWith(PREFIX_THEME_REF)) {
            String remainder = url.substring(PREFIX_THEME_REF.length());
            if (url.startsWith(ATTR_REF_PREFIX)) {
                url = PREFIX_RESOURCE_REF + url.substring(PREFIX_THEME_REF.length());
                return hasResourceItem(url);
            }
            int colon = url.indexOf(':');
            if (colon != -1) {
                // Convert from ?android:progressBarStyleBig to ?android:attr/progressBarStyleBig
                if (remainder.indexOf('/', colon) == -1) {
                    remainder = remainder.substring(0, colon) + RESOURCE_CLZ_ATTR + '/'
                            + remainder.substring(colon);
                }
                url = PREFIX_RESOURCE_REF + remainder;
                return hasResourceItem(url);
            } else {
                int slash = url.indexOf('/');
                if (slash == -1) {
                    url = PREFIX_RESOURCE_REF + RESOURCE_CLZ_ATTR + '/' + remainder;
                    return hasResourceItem(url);
                }
            }
        }

        if (!url.startsWith(PREFIX_RESOURCE_REF)) {
            return false;
        }

        assert url.startsWith("@") || url.startsWith("?") : url;

        ensureInitialized();

        int typeEnd = url.indexOf('/', 1);
        if (typeEnd != -1) {
            int nameBegin = typeEnd + 1;

            // Skip @ and @+
            int typeBegin = url.startsWith("@+") ? 2 : 1; //$NON-NLS-1$

            int colon = url.lastIndexOf(':', typeEnd);
            if (colon != -1) {
                typeBegin = colon + 1;
            }
            String typeName = url.substring(typeBegin, typeEnd);
            ResourceType type = ResourceType.fromXmlValue(typeName);
            if (type != null) {
                String name = url.substring(nameBegin);
                return hasResourceItem(type, name);
            }
        }

        return false;
    }

    /**
     * Returns true if this resource repository contains a resource of the given name.
     *
     * @param type the type of resource to look up
     * @param name the name of the resource
     * @return true if the resource is known
     */
    public boolean hasResourceItem(@NonNull ResourceType type, @NonNull String name) {
        ensureInitialized();

        Map<String, ResourceItem> map = mResourceMap.get(type);

        if (map != null) {

            ResourceItem resourceItem = map.get(name);
            if (resourceItem != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a {@link ResourceItem} matching the given {@link ResourceType} and name. If none
     * exist, it creates one.
     *
     * @param type the resource type
     * @param name the name of the resource.
     * @return A resource item matching the type and name.
     */
    @NonNull
    public ResourceItem getResourceItem(@NonNull ResourceType type, @NonNull String name) {
        ensureInitialized();

        // looking for an existing ResourceItem with this type and name
        ResourceItem item = findDeclaredResourceItem(type, name);

        // create one if there isn't one already, or if the existing one is inlined, since
        // clearly we need a non inlined one (the inline one is removed too)
        if (item == null || item.isDeclaredInline()) {
            ResourceItem oldItem = item;
            item = createResourceItem(name);

            Map<String, ResourceItem> map = mResourceMap.get(type);

            if (map == null) {
                if (isFrameworkRepository()) {
                    // Pick initial size for the maps. Also change the load factor to 1.0
                    // to avoid rehashing the whole table when we (as expected) get near
                    // the known rough size of each resource type map.
                    int size;
                    switch (type) {
                        // Based on counts in API 16. Going back to API 10, the counts
                        // are roughly 25-50% smaller (e.g. compared to the top 5 types below
                        // the fractions are 1107 vs 1734, 831 vs 1508, 895 vs 1255,
                        // 733 vs 1064 and 171 vs 783.
                        case PUBLIC:           size = 1734; break;
                        case DRAWABLE:         size = 1508; break;
                        case STRING:           size = 1255; break;
                        case ATTR:             size = 1064; break;
                        case STYLE:             size = 783; break;
                        case ID:                size = 347; break;
                        case STYLEABLE:
                            size = 210;
                            break;
                        case LAYOUT:            size = 187; break;
                        case COLOR:             size = 120; break;
                        case ANIM:               size = 95; break;
                        case DIMEN:              size = 81; break;
                        case BOOL:               size = 54; break;
                        case INTEGER:            size = 52; break;
                        case ARRAY:              size = 51; break;
                        case PLURALS:            size = 20; break;
                        case XML:                size = 14; break;
                        case INTERPOLATOR :      size = 13; break;
                        case ANIMATOR:            size = 8; break;
                        case RAW:                 size = 4; break;
                        case MENU:                size = 2; break;
                        case MIPMAP:              size = 2; break;
                        case FRACTION:            size = 1; break;
                        default:
                            size = 2;
                    }
                    map = new HashMap<>(size, 1.0f);
                } else {
                    map = new HashMap<>();
                }
                mResourceMap.put(type, map);
            }

            map.put(item.getName(), item);

            if (oldItem != null) {
                map.remove(oldItem.getName());
            }
        }

        return item;
    }

    /**
     * Creates a resource item with the given name.
     * @param name the name of the resource
     * @return a new ResourceItem (or child class) instance.
     */
    @NonNull
    protected abstract ResourceItem createResourceItem(@NonNull String name);

    /**
     * Processes a folder and adds it to the list of existing folders.
     * @param folder the folder to process
     * @return the ResourceFolder created from this folder, or null if the process failed.
     */
    @Nullable
    public ResourceFolder processFolder(@NonNull IAbstractFolder folder) {
        ensureInitialized();

        // split the name of the folder in segments.
        String[] folderSegments = folder.getName().split(SdkConstants.RES_QUALIFIER_SEP);

        // get the enum for the resource type.
        ResourceFolderType type = ResourceFolderType.getTypeByName(folderSegments[0]);

        if (type != null) {
            // get the folder configuration.
            FolderConfiguration config = FolderConfiguration.getConfig(folderSegments);

            if (config != null) {
                return add(type, config, folder);
            }
        }

        return null;
    }

    /**
     * Returns a list of {@link ResourceFolder} for a specific {@link ResourceFolderType}.
     *
     * @param type The {@link ResourceFolderType}
     */
    @Nullable
    public List<ResourceFolder> getFolders(@NonNull ResourceFolderType type) {
        ensureInitialized();

        return mFolderMap.get(type);
    }

    @NonNull
    public List<ResourceType> getAvailableResourceTypes() {
        ensureInitialized();

        List<ResourceType> list = new ArrayList<ResourceType>();

        // For each key, we check if there's a single ResourceType match.
        // If not, we look for the actual content to give us the resource type.

        for (ResourceFolderType folderType : mFolderMap.keySet()) {
            List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);
            if (types.size() == 1) {
                // before we add it we check if it's not already present, since a ResourceType
                // could be created from multiple folders, even for the folders that only create
                // one type of resource (drawable for instance, can be created from drawable/ and
                // values/)
                if (!list.contains(types.get(0))) {
                    list.add(types.get(0));
                }
            } else {
                // there isn't a single resource type out of this folder, so we look for all
                // content.
                List<ResourceFolder> folders = mFolderMap.get(folderType);
                if (folders != null) {
                    for (ResourceFolder folder : folders) {
                        Collection<ResourceType> folderContent = folder.getResourceTypes();

                        // then we add them, but only if they aren't already in the list.
                        for (ResourceType folderResType : folderContent) {
                            if (!list.contains(folderResType)) {
                                list.add(folderResType);
                            }
                        }
                    }
                }
            }
        }

        return list;
    }

    /**
     * Returns a list of {@link ResourceItem} matching a given {@link ResourceType}.
     * @param type the type of the resource items to return
     * @return a non null collection of resource items
     */
    @NonNull
    public Collection<ResourceItem> getResourceItemsOfType(@NonNull ResourceType type) {
        ensureInitialized();

        Map<String, ResourceItem> map = mResourceMap.get(type);

        if (map == null) {
            return Collections.emptyList();
        }

        Collection<ResourceItem> roList = mReadOnlyListMap.get(map);
        if (roList == null) {
            roList = Collections.unmodifiableCollection(map.values());
            mReadOnlyListMap.put(map, roList);
        }

        return roList;
    }

    /**
     * Returns whether the repository has resources of a given {@link ResourceType}.
     * @param type the type of resource to check.
     * @return true if the repository contains resources of the given type, false otherwise.
     */
    public boolean hasResourcesOfType(@NonNull ResourceType type) {
        ensureInitialized();

        Map<String, ResourceItem> items = mResourceMap.get(type);
        return (items != null && !items.isEmpty());
    }

    /**
     * Returns the {@link ResourceFolder} associated with a {@link IAbstractFolder}.
     * @param folder The {@link IAbstractFolder} object.
     * @return the {@link ResourceFolder} or null if it was not found.
     */
    @Nullable
    public ResourceFolder getResourceFolder(@NonNull IAbstractFolder folder) {
        ensureInitialized();

        Collection<List<ResourceFolder>> values = mFolderMap.values();

        for (List<ResourceFolder> list : values) {
            for (ResourceFolder resFolder : list) {
                IAbstractFolder wrapper = resFolder.getFolder();
                if (wrapper.equals(folder)) {
                    return resFolder;
                }
            }
        }

        return null;
    }

    /**
     * Returns the {@link ResourceFile} matching the given name,
     * {@link ResourceFolderType} and configuration.
     * <p>
     * This only works with files generating one resource named after the file
     * (for instance, layouts, bitmap based drawable, xml, anims).
     *
     * @param name the resource name or file name
     * @param type the folder type search for
     * @param config the folder configuration to match for
     * @return the matching file or <code>null</code> if no match was found.
     */
    @Nullable
    public ResourceFile getMatchingFile(
            @NonNull String name,
            @NonNull ResourceFolderType type,
            @NonNull FolderConfiguration config) {
        List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(type);
        for (ResourceType t : types) {
            if (t == ResourceType.ID) {
                continue;
            }
            ResourceFile match = getMatchingFile(name, t, config);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    /**
     * Returns the {@link ResourceFile} matching the given name,
     * {@link ResourceType} and configuration.
     * <p>
     * This only works with files generating one resource named after the file
     * (for instance, layouts, bitmap based drawable, xml, anims).
     *
     * @param name the resource name or file name
     * @param type the folder type search for
     * @param config the folder configuration to match for
     * @return the matching file or <code>null</code> if no match was found.
     */
    @Nullable
    public ResourceFile getMatchingFile(
            @NonNull String name,
            @NonNull ResourceType type,
            @NonNull FolderConfiguration config) {
        ensureInitialized();

        String resourceName = name;
        int dot = resourceName.indexOf('.');
        if (dot != -1) {
            resourceName = resourceName.substring(0, dot);
        }

        Map<String, ResourceItem> items = mResourceMap.get(type);
        if (items != null) {
            ResourceItem item = items.get(resourceName);
            if (item != null) {
                List<ResourceFile> files = item.getSourceFileList();
                if (files != null) {
                    if (files.size() > 1) {
                        ResourceValue value = item.getResourceValue(type, config,
                                isFrameworkRepository());
                        if (value != null) {
                            String v = value.getValue();
                            if (v != null) {
                                ResourceUrl url = ResourceUrl.parse(v);
                                if (url != null) {
                                    return getMatchingFile(url.name, url.type, config);
                                } else {
                                    // Looks like the resource value is pointing to a file
                                    // It's most likely one of the source files for this
                                    // resource item, so check those first
                                    for (ResourceFile f : files) {
                                        if (v.equals(f.getFile().getOsLocation())) {
                                            // Found the file
                                            return f;
                                        }
                                    }

                                    // No; look up the resource file from the full path
                                    File file = new File(v);
                                    if (file.exists()) {
                                        ResourceFile f = findResourceFile(file);
                                        if (f != null) {
                                            return f;
                                        }
                                    }
                                }
                            }
                        }
                    } else if (files.size() == 1) {
                        // Single file: see if it matches
                        ResourceFile matchingFile = files.get(0);
                        if (matchingFile.getFolder().getConfiguration().isMatchFor(config)) {
                            return matchingFile;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Looks up the {@link ResourceFile} for the given {@link File}, if possible
     *
     * @param file the file
     * @return the corresponding {@link ResourceFile}, or null if not a known {@link ResourceFile}
     */
    @Nullable
    protected ResourceFile findResourceFile(@NonNull File file) {
        // Look up the right resource file for this path
        String parentName = file.getParentFile().getName();
        IAbstractFolder folder = getResFolder().getFolder(parentName);
        if (folder != null) {
            ResourceFolder resourceFolder = getResourceFolder(folder);
            if (resourceFolder == null) {
                FolderConfiguration configForFolder = FolderConfiguration
                        .getConfigForFolder(parentName);
                if (configForFolder != null) {
                    ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
                    if (folderType != null) {
                        resourceFolder = add(folderType, configForFolder, folder);
                    }
                }
            }
            if (resourceFolder != null) {
                ResourceFile resourceFile = resourceFolder.getFile(file.getName());
                if (resourceFile != null) {
                    return resourceFile;
                }
            }
        }

        return null;
    }

    /**
     * Returns the list of source files for a given resource.
     * Optionally, if a {@link FolderConfiguration} is given, then only the best
     * match for this config is returned.
     *
     * @param type the type of the resource.
     * @param name the name of the resource.
     * @param referenceConfig an optional config for which only the best match will be returned.
     *
     * @return a list of files generating this resource or null if it was not found.
     */
    @Nullable
    public List<ResourceFile> getSourceFiles(@NonNull ResourceType type, @NonNull String name,
            @Nullable FolderConfiguration referenceConfig) {
        ensureInitialized();

        Collection<ResourceItem> items = getResourceItemsOfType(type);

        for (ResourceItem item : items) {
            if (name.equals(item.getName())) {
                if (referenceConfig != null) {
                    ResourceFile match =
                            referenceConfig.findMatchingConfigurable(item.getSourceFileList());
                    if (match != null) {
                        return Collections.singletonList((ResourceFile) match);
                    }

                    return null;
                }
                return item.getSourceFileList();
            }
        }

        return null;
    }

    /**
     * Returns the resources values matching a given {@link FolderConfiguration}.
     *
     * @param referenceConfig the configuration that each value must match.
     * @return a map with guaranteed to contain an entry for each {@link ResourceType}
     */
    @NonNull
    public Map<ResourceType, ResourceValueMap> getConfiguredResources(
            @NonNull FolderConfiguration referenceConfig) {
        ensureInitialized();

        return doGetConfiguredResources(referenceConfig);
    }

    /**
     * Returns the resources values matching a given {@link FolderConfiguration} for the current
     * project.
     *
     * @param referenceConfig the configuration that each value must match.
     * @return a map with guaranteed to contain an entry for each {@link ResourceType}
     */
    @NonNull
    protected final Map<ResourceType, ResourceValueMap> doGetConfiguredResources(
            @NonNull FolderConfiguration referenceConfig) {
        ensureInitialized();

        Map<ResourceType, ResourceValueMap> map =
            new EnumMap<ResourceType, ResourceValueMap>(ResourceType.class);

        for (ResourceType key : ResourceType.values()) {
            // get the local results and put them in the map
            map.put(key, getConfiguredResource(key, referenceConfig));
        }

        return map;
    }

    /**
     * Returns the sorted list of languages used in the resources.
     */
    @NonNull
    public SortedSet<String> getLanguages() {
        ensureInitialized();

        SortedSet<String> set = new TreeSet<String>();

        Collection<List<ResourceFolder>> folderList = mFolderMap.values();
        for (List<ResourceFolder> folderSubList : folderList) {
            for (ResourceFolder folder : folderSubList) {
                FolderConfiguration config = folder.getConfiguration();
                LocaleQualifier locale = config.getLocaleQualifier();
                if (locale != null && locale.hasLanguage()) {
                    set.add(locale.getLanguage());
                }
            }
        }

        return set;
    }

    /**
     * Returns the sorted list of regions used in the resources with the given language.
     *
     * @param currentLanguage the current language the region must be associated with.
     */
    @NonNull
    public SortedSet<String> getRegions(@NonNull String currentLanguage) {
        ensureInitialized();

        SortedSet<String> set = new TreeSet<String>();

        Collection<List<ResourceFolder>> folderList = mFolderMap.values();
        for (List<ResourceFolder> folderSubList : folderList) {
            for (ResourceFolder folder : folderSubList) {
                FolderConfiguration config = folder.getConfiguration();

                // get the language
                LocaleQualifier locale = config.getLocaleQualifier();
                if (locale != null && currentLanguage.equals(locale.getLanguage())
                        && locale.getRegion() != null) {
                    set.add(locale.getRegion());
                }
            }
        }

        return set;
    }

    /**
     * Loads the resources.
     */
    public void loadResources() {
        clear();
        ensureInitialized();
    }

    protected void removeFile(@NonNull Collection<ResourceType> types,
            @NonNull ResourceFile file) {
        ensureInitialized();

        for (ResourceType type : types) {
            removeFile(type, file);
        }
    }

    protected void removeFile(@NonNull ResourceType type, @NonNull ResourceFile file) {
        Map<String, ResourceItem> map = mResourceMap.get(type);
        if (map != null) {
            Collection<ResourceItem> values = map.values();
            List<ResourceItem> toDelete = null;
            for (ResourceItem item : values) {
                item.removeFile(file);
                if (item.hasNoSourceFile()) {
                    if (toDelete == null) {
                        toDelete = new ArrayList<ResourceItem>(values.size());
                    }
                    toDelete.add(item);
                }
            }
            if (toDelete != null) {
                for (ResourceItem item : toDelete) {
                    map.remove(item.getName());
                }
            }
        }
    }

    /**
     * Returns a map of (resource name, resource value) for the given {@link ResourceType}.
     * <p>The values returned are taken from the resource files best matching a given
     * {@link FolderConfiguration}.
     *
     * @param type the type of the resources.
     * @param referenceConfig the configuration to best match.
     */
    @NonNull
    private ResourceValueMap getConfiguredResource(@NonNull ResourceType type,
            @NonNull FolderConfiguration referenceConfig) {
        // get the resource item for the given type
        Map<String, ResourceItem> items = mResourceMap.get(type);
        if (items == null) {
            return ResourceValueMap.create();
        }

        // create the map
        ResourceValueMap map = ResourceValueMap.createWithExpectedSize(items.size());

        for (ResourceItem item : items.values()) {
            ResourceValue value = item.getResourceValue(type, referenceConfig,
                    isFrameworkRepository());
            if (value != null) {
                map.put(item.getName(), value);
            }
        }

        return map;
    }

    /**
     * Cleans up the repository of resource items that have no source file anymore.
     */
    public void postUpdateCleanUp() {
        // Since removed files/folders remove source files from existing ResourceItem, loop through
        // all resource items and remove the ones that have no source files.

        Collection<Map<String, ResourceItem>> maps = mResourceMap.values();
        for (Map<String, ResourceItem> map : maps) {
            Set<String> keySet = map.keySet();
            Iterator<String> iterator = keySet.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                ResourceItem resourceItem = map.get(name);
                if (resourceItem.hasNoSourceFile()) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Looks up an existing {@link ResourceItem} by {@link ResourceType} and name.
     * Ignores inline resources.
     *
     * @param type the resource type.
     * @param name the resource name.
     * @return the existing ResourceItem or null if no match was found.
     */
    @Nullable
    private ResourceItem findDeclaredResourceItem(@NonNull ResourceType type,
            @NonNull String name) {
        Map<String, ResourceItem> map = mResourceMap.get(type);

        if (map != null) {
            ResourceItem resourceItem = map.get(name);
            if (resourceItem != null && !resourceItem.isDeclaredInline()) {
                return resourceItem;
            }
        }

        return null;
    }
}

