/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.scottrosenquist.watchfacethree;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;


import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
public class WatchfaceThree extends CanvasWatchFaceService {

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_BEATS_PER_SECOND = 10;
    private static final long INTERACTIVE_UPDATE_RATE_MS = 1000 / INTERACTIVE_BEATS_PER_SECOND;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WatchfaceThree.Engine> weakReference;

        public EngineHandler(WatchfaceThree.Engine reference) {
            weakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchfaceThree.Engine engine = weakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 10f;
        private static final float MINUTE_STROKE_WIDTH = 6f;
        private static final float SECOND_STROKE_WIDTH = 3f;
        private static final float SECONDS_TICK_STROKE_WIDTH = 2.5f;
        private static final float SECONDS_NUMBER_TICK_FONT_SIZE = 15f;
        private static final float HOURS_TICK_STROKE_WIDTH = 6f;
        private static final float CIRCLE_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 20f;

        /* Handler to update the time once a second in interactive mode. */
        private final Handler updateTimeHandler = new EngineHandler(this);
        private Calendar calendar;
        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean registeredTimeZoneReceiver = false;
        private float centerX;
        private float centerY;
        private float secondHandLength;
        private float minuteHandLength;
        private float hourHandLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int watchHandColor;
        private int watchHandHighlightColor;
        private Paint hourPaint;
        private Paint minutePaint;
        private Paint secondPaint;
        private Paint secondsTickPaint;
        private Paint secondsNumberTickPaint;
        private Paint hoursTickPaint;
        private Paint circlePaint;
        private Paint backgroundPaint;
        private boolean ambient;
        private boolean lowBitAmbient;
        private boolean burnInProtection;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchfaceThree.this)
                    .setStatusBarGravity(Gravity.CENTER_VERTICAL)
                    .build());

            backgroundPaint = new Paint();
            backgroundPaint.setColor(Color.BLACK);

            /* Set defaults for colors */
            watchHandColor = Color.WHITE;
            watchHandHighlightColor = Color.RED;

            hourPaint = new Paint();
            hourPaint.setColor(watchHandColor);
            hourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            hourPaint.setAntiAlias(true);
            hourPaint.setStrokeCap(Paint.Cap.BUTT);

            minutePaint = new Paint();
            minutePaint.setColor(watchHandColor);
            minutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            minutePaint.setAntiAlias(true);
            minutePaint.setStrokeCap(Paint.Cap.BUTT);

            secondPaint = new Paint();
            secondPaint.setColor(watchHandHighlightColor);
            secondPaint.setStrokeWidth(SECOND_STROKE_WIDTH);
            secondPaint.setAntiAlias(true);
            secondPaint.setStrokeCap(Paint.Cap.BUTT);

            secondsTickPaint = new Paint();
            secondsTickPaint.setColor(watchHandColor);
            secondsTickPaint.setStrokeWidth(SECONDS_TICK_STROKE_WIDTH);
            secondsTickPaint.setAntiAlias(true);
            secondsTickPaint.setStyle(Paint.Style.STROKE);

            secondsNumberTickPaint = new Paint();
            secondsNumberTickPaint.setColor(watchHandColor);
            secondsNumberTickPaint.setAntiAlias(true);
            secondsNumberTickPaint.setTextSize(SECONDS_NUMBER_TICK_FONT_SIZE);

            hoursTickPaint = new Paint();
            hoursTickPaint.setColor(watchHandColor);
            hoursTickPaint.setStrokeWidth(HOURS_TICK_STROKE_WIDTH);
            hoursTickPaint.setAntiAlias(true);
            hoursTickPaint.setStyle(Paint.Style.STROKE);

            circlePaint = new Paint();
            circlePaint.setColor(watchHandColor);
            circlePaint.setStrokeWidth(CIRCLE_STROKE_WIDTH);
            circlePaint.setAntiAlias(true);
            circlePaint.setStyle(Paint.Style.STROKE);

            calendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            ambient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (ambient) {
                hourPaint.setColor(Color.WHITE);
                minutePaint.setColor(Color.WHITE);
                secondPaint.setColor(Color.WHITE);
                secondsTickPaint.setColor(Color.WHITE);
                secondsNumberTickPaint.setColor(Color.WHITE);
                hoursTickPaint.setColor(Color.WHITE);
                circlePaint.setColor(Color.WHITE);

                hourPaint.setAntiAlias(!lowBitAmbient);
                minutePaint.setAntiAlias(!lowBitAmbient);
                secondPaint.setAntiAlias(!lowBitAmbient);
                secondsTickPaint.setAntiAlias(!lowBitAmbient);
                secondsNumberTickPaint.setAntiAlias(!lowBitAmbient);
                hoursTickPaint.setAntiAlias(!lowBitAmbient);
                circlePaint.setAntiAlias(!lowBitAmbient);

            } else {
                hourPaint.setColor(watchHandColor);
                minutePaint.setColor(watchHandColor);
                secondPaint.setColor(watchHandHighlightColor);
                secondsTickPaint.setColor(watchHandColor);
                secondsNumberTickPaint.setColor(watchHandColor);
                hoursTickPaint.setColor(watchHandColor);
                circlePaint.setColor(watchHandColor);

                hourPaint.setAntiAlias(true);
                minutePaint.setAntiAlias(true);
                secondPaint.setAntiAlias(true);
                secondsTickPaint.setAntiAlias(true);
                secondsNumberTickPaint.setAntiAlias(true);
                hoursTickPaint.setAntiAlias(true);
                circlePaint.setAntiAlias(true);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            centerX = width / 2f;
            centerY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            secondHandLength = (float) (centerX * 0.875);
            minuteHandLength = (float) (centerX * 0.75);
            hourHandLength = (float) (centerX * 0.5);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);

            if (ambient && (lowBitAmbient || burnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (ambient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(Color.BLACK);
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerHourTickRadius = centerX - 60;
            float outerHourTickRadius = centerX - 25;
            float innerSecondTickRadius = centerX - 20;
            float outerSecondTickRadius = centerX;
            float secondsNumberTickRadius = centerX - 10;
            int secondsNumberRotation = 0;
            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX;
                float innerY;
                float outerX;
                float outerY;
                switch (tickIndex) {
                    case 0:
                    case 5:
                    case 10:
                    case 15:
                    case 20:
                    case 25:
                    case 30:
                    case 35:
                    case 40:
                    case 45:
                    case 50:
                    case 55:
                        innerX = (float) Math.sin(tickRot) * innerHourTickRadius;
                        innerY = (float) -Math.cos(tickRot) * innerHourTickRadius;
                        outerX = (float) Math.sin(tickRot) * outerHourTickRadius;
                        outerY = (float) -Math.cos(tickRot) * outerHourTickRadius;
                        canvas.drawLine(centerX + innerX, centerY + innerY, centerX + outerX, centerY + outerY, hoursTickPaint);

                        String tickString = String.valueOf(tickIndex);
                        Rect textBounds = new Rect();
                        secondsNumberTickPaint.getTextBounds(tickString, 0, tickString.length(), textBounds);
                        float secondsNumberX = (float) Math.sin(tickRot) * secondsNumberTickRadius;
                        float secondsNumberY = (float) -Math.cos(tickRot) * secondsNumberTickRadius;
                        float secondsNumberAdjustedX = secondsNumberX - textBounds.width() / 2f - textBounds.left;
                        float secondsNumberAdjustedY = secondsNumberY + textBounds.height() / 2f - textBounds.bottom;

                        canvas.save();

                        switch (tickIndex) {
                            case 20:
                            case 25:
                            case 30:
                            case 35:
                            case 40:
                                secondsNumberRotation = tickIndex * 6 + 180;
                                break;
                            default:
                                secondsNumberRotation = tickIndex * 6;
                                break;
                        }

                        canvas.rotate(secondsNumberRotation, centerX + secondsNumberX, centerY + secondsNumberY);

                        canvas.drawText(String.valueOf(tickIndex), centerX + secondsNumberAdjustedX, centerY + secondsNumberAdjustedY, secondsNumberTickPaint);

                        canvas.restore();
                        break;
                    default:
                        innerX = (float) Math.sin(tickRot) * innerSecondTickRadius;
                        innerY = (float) -Math.cos(tickRot) * innerSecondTickRadius;
                        outerX = (float) Math.sin(tickRot) * outerSecondTickRadius;
                        outerY = (float) -Math.cos(tickRot) * outerSecondTickRadius;
                        canvas.drawLine(centerX + innerX, centerY + innerY, centerX + outerX, centerY + outerY, secondsTickPaint);
                        break;
                }

            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */

//            calendar.setTimeInMillis(54569000); // Screen Shot Time
//            calendar.setTimeInMillis(61200000); // Midnight (Hand Alignment)

            final float seconds = (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minuteHandOffset = calendar.get(Calendar.SECOND) / 10f;
            final float minutesRotation = calendar.get(Calendar.MINUTE) * 6f + minuteHandOffset;

            final float hourHandOffset = calendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (calendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            drawHourHand(canvas, hoursRotation);

            drawMinuteHand(canvas, minutesRotation);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!ambient) {
                drawSecondHand(canvas, secondsRotation);
            }
        }

        private void drawHourHand(Canvas canvas, float hoursRotation) {
            canvas.save();

            canvas.rotate(hoursRotation, centerX, centerY);
            canvas.drawLine(
                    centerX,
                    centerY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    centerX,
                    centerY - hourHandLength,
                    hourPaint);

            canvas.restore();
        }

        private void drawMinuteHand(Canvas canvas, float minutesRotation) {
            canvas.save();

            canvas.rotate(minutesRotation, centerX, centerY);
            canvas.drawLine(
                    centerX,
                    centerY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    centerX,
                    centerY - minuteHandLength,
                    minutePaint);

            canvas.restore();
        }

        private void drawSecondHand(Canvas canvas, float secondsRotation) {
            canvas.save();

            canvas.rotate(secondsRotation, centerX, centerY);
            canvas.drawLine(
                    centerX,
                    centerY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    centerX,
                    centerY - secondHandLength,
                    secondPaint);

            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchfaceThree.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            WatchfaceThree.this.unregisterReceiver(timeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #updateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !ambient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
