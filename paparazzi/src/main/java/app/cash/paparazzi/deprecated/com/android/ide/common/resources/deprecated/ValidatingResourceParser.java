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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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
public class ValidatingResourceParser {
    private final boolean mIsFramework;
    private ScanningContext mContext;

    /**
     * Creates a new {@link ValidatingResourceParser}
     *
     * @param context a context object with state for the current update, such
     *            as a place to stash errors encountered
     * @param isFramework true if scanning a framework resource
     */
    public ValidatingResourceParser(
            @NonNull ScanningContext context,
            boolean isFramework) {
        mContext = context;
        mIsFramework = isFramework;
    }

    /**
     * Parse the given input and return false if it contains errors, <b>or</b> if
     * the context is already tagged as needing a full aapt run.
     *
     * @param path the full OS path to the file being parsed
     * @param input the input stream of the XML to be parsed (will be closed by this method)
     * @return true if parsing succeeds and false if it fails
     * @throws IOException if reading the contents fails
     */
    public boolean parse(final String path, InputStream input)
            throws IOException {
        // No need to validate framework files
        if (mIsFramework) {
            try {
                Closeables.close(input, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
            return true;
        }
        if (mContext.needsFullAapt()) {
            try {
                Closeables.close(input, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
            return false;
        }

        KXmlParser parser = new KXmlParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);

            if (input instanceof FileInputStream) {
                input = new BufferedInputStream(input);
            }
            parser.setInput(input, SdkConstants.UTF_8);

            return parse(path, parser);
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

    private boolean parse(String path, KXmlParser parser)
            throws XmlPullParserException, IOException {
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
                            return false;
                        }
                    }
                }
            } else if (event == XmlPullParser.END_DOCUMENT) {
                break;
            }
        }

        return true;
    }
}
