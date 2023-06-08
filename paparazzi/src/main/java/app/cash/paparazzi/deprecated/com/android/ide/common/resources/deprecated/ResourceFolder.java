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
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public final class ResourceFolder implements Configurable {
    final ResourceFolderType mType;
    final FolderConfiguration mConfiguration;
    IAbstractFolder mFolder;
    List<ResourceFile> mFiles;
    Map<String, ResourceFile> mNames;
    private final ResourceRepository mRepository;

    /**
     * Creates a new {@link ResourceFolder}
     * @param type The type of the folder
     * @param config The configuration of the folder
     * @param folder The associated {@link IAbstractFolder} object.
     * @param repository The associated {@link ResourceRepository}
     */
    protected ResourceFolder(ResourceFolderType type, FolderConfiguration config,
      app.cash.paparazzi.deprecated.com.android.io.IAbstractFolder folder, ResourceRepository repository) {
        mType = type;
        mConfiguration = config;
        mFolder = folder;
        mRepository = repository;
    }

    /**
     * Processes a file and adds it to its parent folder resource.
     *
     * @param file the underlying resource file.
     * @param kind the file change kind.
     * @param context a context object with state for the current update, such
     *            as a place to stash errors encountered
     * @return the {@link ResourceFile} that was created.
     */
    public ResourceFile processFile(IAbstractFile file, ResourceDeltaKind kind,
            ScanningContext context) {
        // look for this file if it's already been created
        ResourceFile resFile = getFile(file, context);

        if (resFile == null) {
            if (kind != ResourceDeltaKind.REMOVED) {
                // create a ResourceFile for it.

                resFile = createResourceFile(file);
                resFile.load(context);

                // add it to the folder
                addFile(resFile);
            }
        } else {
            if (kind == ResourceDeltaKind.REMOVED) {
                removeFile(resFile, context);
            } else {
                resFile.update(context);
            }
        }

        return resFile;
    }

    private ResourceFile createResourceFile(IAbstractFile file) {
        // check if that's a single or multi resource type folder. We have a special case
        // for ID generating resource types (layout/menu, and XML drawables, etc.).
        // MultiResourceFile handles the case when several resource types come from a single file
        // (values files).

        ResourceFile resFile;
        if (mType != ResourceFolderType.VALUES) {
            if (FolderTypeRelationship.isIdGeneratingFolderType(mType) &&
                SdkUtils.endsWithIgnoreCase(file.getName(), SdkConstants.DOT_XML)) {
                List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(mType);
                ResourceType primaryType = types.get(0);
                resFile = new IdGeneratingResourceFile(file, this, primaryType);
            } else {
                resFile = new SingleResourceFile(file, this);
            }
        } else {
            resFile = new MultiResourceFile(file, this);
        }
        return resFile;
    }

    /**
     * Adds a {@link ResourceFile} to the folder.
     *
     * @param file The {@link ResourceFile}.
     */
    @VisibleForTesting
    public void addFile(ResourceFile file) {
        if (mFiles == null) {
            int initialSize = 16;
            if (mRepository.isFrameworkRepository()) {
                String name = mFolder.getName();
                // Pick some reasonable initial sizes for framework data structures
                // since they are typically (a) large and (b) their sizes are roughly known
                // in advance
                switch (mType) {
                    case DRAWABLE: {
                        // See if it's one of the -mdpi, -hdpi etc folders which
                        // are large (~1250 items)
                        int index = name.indexOf('-');
                        if (index == -1) {
                            initialSize = 230; // "drawable" folder
                        } else {
                            index = name.indexOf('-', index + 1);
                            if (index == -1) {
                                // One of the "drawable-<density>" folders
                                initialSize = 1260;
                            } else {
                                // "drawable-sw600dp-hdpi" etc
                                initialSize = 30;
                            }
                        }
                        break;
                    }
                    case LAYOUT: {
                        // The main layout folder has about ~185 layouts in it;
                        // the others are small
                        if (name.indexOf('-') == -1) {
                            initialSize = 200;
                        }
                        break;
                    }
                    case VALUES: {
                        if (name.indexOf('-') == -1) {
                            initialSize = 32;
                        } else {
                            initialSize = 4;
                        }
                        break;
                    }
                    case ANIM: initialSize = 85; break;
                    case COLOR: initialSize = 32; break;
                    case RAW: initialSize = 4; break;
                    default:
                        // Stick with the 16 default
                        break;
                }
            }

            mFiles = new ArrayList<ResourceFile>(initialSize);
            mNames = new HashMap<String, ResourceFile>(initialSize, 2.0f);
        }

        mFiles.add(file);
        mNames.put(file.getFile().getName(), file);
    }

    protected void removeFile(ResourceFile file, ScanningContext context) {
        file.dispose(context);
        mFiles.remove(file);
        mNames.remove(file.getFile().getName());
    }

    protected void dispose(ScanningContext context) {
        if (mFiles != null) {
            for (ResourceFile file : mFiles) {
                file.dispose(context);
            }

            mFiles.clear();
            mNames.clear();
        }
    }

    /**
     * Returns the {@link IAbstractFolder} associated with this object.
     */
    public IAbstractFolder getFolder() {
        return mFolder;
    }

    /**
     * Returns the {@link ResourceFolderType} of this object.
     */
    public ResourceFolderType getType() {
        return mType;
    }

    public ResourceRepository getRepository() {
        return mRepository;
    }

    /**
     * Returns the list of {@link ResourceType}s generated by the files inside this folder.
     */
    public Collection<ResourceType> getResourceTypes() {
        ArrayList<ResourceType> list = new ArrayList<ResourceType>();

        if (mFiles != null) {
            for (ResourceFile file : mFiles) {
                Collection<ResourceType> types = file.getResourceTypes();

                // loop through those and add them to the main list,
                // if they are not already present
                for (ResourceType resType : types) {
                    if (list.indexOf(resType) == -1) {
                        list.add(resType);
                    }
                }
            }
        }

        return list;
    }

    @Override
    public FolderConfiguration getConfiguration() {
        return mConfiguration;
    }

    /**
     * Returns whether the folder contains a file with the given name.
     * @param name the name of the file.
     */
    public boolean hasFile(String name) {
        if (mNames != null && mNames.containsKey(name)) {
            return true;
        }

        // Note: mNames.containsKey(name) is faster, but doesn't give the same result; this
        // method seems to be called on this ResourceFolder before it has been processed,
        // so we need to use the file system check instead:
        return mFolder.hasFile(name);
    }

    /**
     * Returns the {@link ResourceFile} matching a {@link IAbstractFile} object.
     *
     * @param file The {@link IAbstractFile} object.
     * @param context a context object with state for the current update, such
     *            as a place to stash errors encountered
     * @return the {@link ResourceFile} or null if no match was found.
     */
    private ResourceFile getFile(IAbstractFile file, ScanningContext context) {
        assert mFolder.equals(file.getParentFolder());

        if (mNames != null) {
            ResourceFile resFile = mNames.get(file.getName());
            if (resFile != null) {
                return resFile;
            }
        }

        // If the file actually exists, the resource folder  may not have been
        // scanned yet; add it lazily
        if (file.exists()) {
            ResourceFile resFile = createResourceFile(file);
            resFile.load(context);
            addFile(resFile);
            return resFile;
        }

        return null;
    }

    /**
     * Returns the {@link ResourceFile} matching a given name.
     * @param filename The name of the file to return.
     * @return the {@link ResourceFile} or <code>null</code> if no match was found.
     */
    public ResourceFile getFile(String filename) {
        if (mNames != null) {
            ResourceFile resFile = mNames.get(filename);
            if (resFile != null) {
                return resFile;
            }
        }

        // If the file actually exists, the resource folder  may not have been
        // scanned yet; add it lazily
        IAbstractFile file = mFolder.getFile(filename);
        if (file != null && file.exists()) {
            ResourceFile resFile = createResourceFile(file);
            resFile.load(new ScanningContext());
            addFile(resFile);
            return resFile;
        }

        return null;
    }

    /**
     * Returns whether a file in the folder is generating a resource of a specified type.
     * @param type The {@link ResourceType} being looked up.
     */
    public boolean hasResources(ResourceType type) {
        // Check if the folder type is able to generate resource of the type that was asked.
        // this is a first check to avoid going through the files.
        List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);

        boolean valid = false;
        for (ResourceFolderType rft : folderTypes) {
            if (rft == mType) {
                valid = true;
                break;
            }
        }

        if (valid) {
            if (mFiles != null) {
                for (ResourceFile f : mFiles) {
                    if (f.hasResources(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return mFolder.toString();
    }
}
