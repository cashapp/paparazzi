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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public class ScanningContext {
    private boolean mNeedsFullAapt;
    private List<String> mErrors;

    /** Constructs a new {@link ScanningContext} */
    public ScanningContext() {
        super();
    }

    /** Returns a list of errors encountered during scanning, or null if there were no errors. */
    @Nullable
    public List<String> getErrors() {
        return mErrors;
    }

    /**
     * Adds the given error to the scanning context. The error should use the
     * same syntax as real aapt error messages such that the aapt parser can
     * properly detect the filename, line number, etc.
     *
     * @param error the error message, including file name and line number at
     *            the beginning
     */
    public void addError(@NonNull String error) {
        if (mErrors == null) {
            mErrors = new ArrayList<>();
        }
        mErrors.add(error);
    }

    /**
     * Marks that a full aapt compilation of the resources is necessary because it has
     * detected a change that cannot be incrementally handled.
     */
    protected void requestFullAapt() {
        mNeedsFullAapt = true;
    }

    /**
     * Returns whether this repository has been marked as "dirty"; if one or
     * more of the constituent files have declared that the resource item names
     * that they provide have changed.
     *
     * @return true if a full aapt compilation is required
     */
    public boolean needsFullAapt() {
        return mNeedsFullAapt;
    }

    /**
     * Asks the context to check whether the given attribute name and value is valid
     * in this context.
     *
     * @param uri the XML namespace URI
     * @param name the attribute local name
     * @param value the attribute value
     * @return true if the attribute is valid
     */
    public boolean checkValue(@Nullable String uri, @NonNull String name, @NonNull String value) {
        return true;
    }
}
