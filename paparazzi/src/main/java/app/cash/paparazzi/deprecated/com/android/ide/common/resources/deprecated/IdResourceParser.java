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
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.resources.ResourceType;
import com.google.common.io.Closeables;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @deprecated This class is part of an obsolete resource repository system that is no longer used
 *     in production code. The class is preserved temporarily for LayoutLib tests.
 */
@Deprecated
public class IdResourceParser {
    private final IValueResourceRepository mRepository;
    private final boolean mIsFramework;
    private ScanningContext mContext;

    /**
     * Creates a new {@link IdResourceParser}
     *
     * @param repository value repository for registering resource declaration
     * @param context a context object with state for the current update, such
     *            as a place to stash errors encountered
     * @param isFramework true if scanning a framework resource
     */
    public IdResourceParser(
            @NonNull IValueResourceRepository repository,
            @NonNull ScanningContext context,
            boolean isFramework) {
        mRepository = repository;
        mContext = context;
        mIsFramework = isFramework;
    }

    /**
     * Parse the given input and register ids with the given
     * {@link IValueResourceRepository}.
     *
     * @param type the type of resource being scanned
     * @param path the full OS path to the file being parsed
     * @param input the input stream of the XML to be parsed (will be closed by this method)
     * @return true if parsing succeeds and false if it fails
     * @throws IOException if reading the contents fails
     */
    public boolean parse(ResourceType type, final String path, InputStream input)
            throws IOException {
        KXmlParser parser = new KXmlParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

            if (input instanceof FileInputStream) {
                input = new BufferedInputStream(input);
            }
            parser.setInput(input, SdkConstants.UTF_8);

            return parse(type, path, parser);
        } catch (XmlPullParserException e) {
            String message = e.getMessage();

            // Strip off position description
            int index = message.indexOf("(position:"); //$NON-NLS-1$ (Hardcoded in KXml)
            if (index != -1) {
                message = message.substring(0, index);
            }

            String error = String.format("%1$s:%2$d: Error: %3$s", //$NON-NLS-1$
                    path, parser.getLineNumber(), message);
            mContext.addError(error);
            return false;
        } catch (RuntimeException e) {
            // Some exceptions are thrown by the KXmlParser that are not XmlPullParserExceptions,
            // such as this one:
            //    java.lang.RuntimeException: Undefined Prefix: w in org.kxml2.io.KXmlParser@...
            //        at org.kxml2.io.KXmlParser.adjustNsp(Unknown Source)
            //        at org.kxml2.io.KXmlParser.parseStartTag(Unknown Source)
            String message = e.getMessage();
            String error = String.format("%1$s:%2$d: Error: %3$s", //$NON-NLS-1$
                    path, parser.getLineNumber(), message);
            mContext.addError(error);
            return false;
        } finally {
            try {
                Closeables.close(input, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
        }
    }

    private boolean parse(ResourceType type, String path, KXmlParser parser)
            throws XmlPullParserException, IOException {
        boolean valid = true;
        boolean checkForErrors = !mIsFramework && !mContext.needsFullAapt();

        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.START_TAG) {
                for (int i = 0, n = parser.getAttributeCount(); i < n; i++) {
                    String attribute = parser.getAttributeName(i);
                    String value = parser.getAttributeValue(i);
                    assert value != null : attribute;

                    if (checkForErrors) {
                        String uri = parser.getAttributeNamespace(i);
                        if (!mContext.checkValue(uri, attribute, value)) {
                            mContext.requestFullAapt();
                            checkForErrors = false;
                            valid = false;
                        }
                    }

                    if (value.startsWith("@+")) {       //$NON-NLS-1$
                        // Strip out the @+id/ or @+android:id/ section
                        String id = value.substring(value.indexOf('/') + 1);
                        ResourceValue newId =
                                new ResourceValueImpl(
                                        new ResourceReference(
                                                ResourceNamespace.fromBoolean(mIsFramework),
                                                ResourceType.ID,
                                                id),
                                        null);
                        mRepository.addResourceValue(newId);
                    }
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                break;
            }
        }

        return valid;
    }
}
