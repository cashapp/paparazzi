package com.squareup.cash.screenshot.android;

import android.widget.Button;
import com.squareup.cash.screenshot.R;
import com.squareup.cash.screenshot.jvm.Paparazzi;
import org.junit.Rule;
import org.junit.Test;

public class BoostViewTest {
    @Rule
    public Paparazzi paparazzi = new Paparazzi();

    @Test
    public void testViews() {
        Button button = paparazzi.inflate(R.layout.button);

        button.setText("Fuck yeeaaa");

        paparazzi.snapshot();
    }
}
