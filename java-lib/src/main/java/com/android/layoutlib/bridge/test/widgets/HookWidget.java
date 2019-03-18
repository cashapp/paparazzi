/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.layoutlib.bridge.test.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

@SuppressWarnings("unused") // Used from RenderTests
public class HookWidget extends View {
    private static final Runnable NOP_RUNNABLE = () -> {};
    private static Runnable sOnPreDrawHook = NOP_RUNNABLE;
    private static Runnable sOnPreMeasure = NOP_RUNNABLE;
    private static Runnable sOnPreLayout = NOP_RUNNABLE;
    private static Runnable sOnPostDrawHook = NOP_RUNNABLE;
    private static Runnable sOnPostMeasure = NOP_RUNNABLE;
    private static Runnable sOnPostLayout = NOP_RUNNABLE;

    public static void setOnPreDrawHook(Runnable runnable) {
        sOnPreDrawHook = runnable;
    }

    public static void setOnPreMeasure(Runnable runnable) {
        sOnPreMeasure = runnable;
    }

    public static void setOnPreLayout(Runnable runnable) {
        sOnPreLayout = runnable;
    }

    public static void setOnPostDrawHook(Runnable onPostDrawHook) {
        sOnPostDrawHook = onPostDrawHook;
    }

    public static void setOnPostMeasure(Runnable onPostMeasure) {
        sOnPostMeasure = onPostMeasure;
    }

    public static void setOnPostLayout(Runnable onPostLayout) {
        sOnPostLayout = onPostLayout;
    }

    public static void reset() {
        sOnPreDrawHook = NOP_RUNNABLE;
        sOnPreMeasure = NOP_RUNNABLE;
        sOnPreLayout = NOP_RUNNABLE;
        sOnPostDrawHook = NOP_RUNNABLE;
        sOnPostMeasure = NOP_RUNNABLE;
        sOnPostLayout = NOP_RUNNABLE;
    }

    public HookWidget(Context context) {
        super(context);
    }

    public HookWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HookWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HookWidget(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        sOnPreDrawHook.run();

        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        Rect rect = new Rect();
        getDrawingRect(rect);
        canvas.drawRect(rect, paint);

        sOnPostDrawHook.run();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        sOnPreMeasure.run();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        sOnPostMeasure.run();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        sOnPreLayout.run();
        super.onLayout(changed, left, top, right, bottom);
        sOnPostLayout.run();
    }
}
