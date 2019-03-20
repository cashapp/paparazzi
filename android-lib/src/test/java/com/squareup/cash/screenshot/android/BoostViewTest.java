package com.squareup.cash.screenshot.android;

import android.widget.Button;
import com.android.layoutlib.bridge.intensive.RenderTestBase;
import com.android.layoutlib.bridge.intensive.setup.LayoutLibTestCallback;
import com.squareup.cash.screenshot.R;
import com.squareup.cash.screenshot.jvm.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

public class BoostViewTest extends RenderTestBase {
    @Rule
    public Paparazzi paparazzi = new Paparazzi();

    @Test
    public void testVectorDrawable() {
        Button rootView = paparazzi.inflateView(
            R.layout.button,
            new LayoutLibTestCallback(getLogger(), mDefaultClassLoader),
            getSessionParamsBuilder()
        );

        rootView.setText("Fuck yeeaaa");

        paparazzi.snapshot();
    }
}
