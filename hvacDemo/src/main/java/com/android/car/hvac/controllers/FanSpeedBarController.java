/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac.controllers;

import android.util.Log;

import com.android.car.hvac.HvacController;
import com.android.car.hvac.api.FanSpeedApi;
import com.android.car.hvac.ui.FanSpeedBar;
import com.liyangbin.cartrofit.Cartrofit;

import androidx.lifecycle.LifecycleObserver;

/**
 * Controller for the fan speed bar to adjust fan speed.
 */
public class FanSpeedBarController implements LifecycleObserver {
    private final static String TAG = "FanSpeedBarCtrl";

    private final FanSpeedBar mFanSpeedBar;
    private final HvacController mHvacController;
    private int mCurrentFanSpeed;

    private FanSpeedApi mFanSpeedApi = Cartrofit.from(FanSpeedApi.class);

    // Note the following are car specific values.

    public FanSpeedBarController(FanSpeedBar speedBar, HvacController controller) {
        mFanSpeedBar = speedBar;
        mHvacController = controller;
        initialize();
    }

    private void initialize() {
        mFanSpeedBar.setFanspeedButtonClickListener(mClickListener);
        mFanSpeedApi.registerFanSpeedChangeCallback(speed -> handleFanSpeedUpdate(speed, true));
//        mHvacController.registerCallback(mCallback);
        // During initialization, we do not need to animate the changes.
        handleFanSpeedUpdate(mHvacController.getFanSpeed(), false /* animateUpdate */);
    }

    private void handleFanSpeedUpdate(int speed, boolean animateUpdate) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Fan speed bar being set to value: " + speed);
        }

        mCurrentFanSpeed = speed;
        if (mCurrentFanSpeed == FanSpeedApi.MIN_FAN_SPEED) {
            mFanSpeedBar.setOff();
        } else if (mCurrentFanSpeed >= FanSpeedApi.MAX_FAN_SPEED) {
            mFanSpeedBar.setMax();
        } else if (mCurrentFanSpeed < FanSpeedApi.MAX_FAN_SPEED && mCurrentFanSpeed > FanSpeedApi.MIN_FAN_SPEED) {
            // Note car specific values being used:
            // The lowest fanspeed is represented by the off button, the first segment
            // actually represents the second fan speed setting.
            if (animateUpdate) {
                mFanSpeedBar.animateToSpeedSegment(mCurrentFanSpeed - 1);
            } else {
                mFanSpeedBar.setSpeedSegment(mCurrentFanSpeed - 1);
            }
        }
    }

    private FanSpeedBar.FanSpeedButtonClickListener mClickListener
            = new FanSpeedBar.FanSpeedButtonClickListener() {
        @Override
        public void onMaxButtonClicked() {
            mFanSpeedApi.setFanSpeed(FanSpeedApi.MAX_FAN_SPEED);
        }

        @Override
        public void onOffButtonClicked() {
            mFanSpeedApi.setFanSpeed(FanSpeedApi.MIN_FAN_SPEED);
        }

        @Override
        public void onFanSpeedSegmentClicked(int position) {
            // Note car specific values being used:
            // The lowest fanspeed is represented by the off button, the first segment
            // actually represents the second fan speed setting.
            mFanSpeedApi.setFanSpeed(position + 1);
        }
    };

    public interface OnFanSpeedChangeCallback {

        void onFanSpeedChange(int speed);
    }
}
