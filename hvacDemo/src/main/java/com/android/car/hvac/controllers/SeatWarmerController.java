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

import com.android.car.hvac.HvacController;
import com.android.car.hvac.api.SeatWarmerApi;
import com.android.car.hvac.ui.SeatWarmerButton;
import com.liyangbin.cartrofit.Cartrofit;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

/**
 * A controller to handle changes in the heated seat levels.
 */
public class SeatWarmerController implements LifecycleObserver, SeatWarmerApi.OnWarmLevelChangeCallback {
    private final SeatWarmerButton mPassengerSeatButton;
    private final SeatWarmerButton mDriverSeatButton;

    private final HvacController mHvacController;

    private final SeatWarmerApi mSeatWarmerApi = Cartrofit.from(SeatWarmerApi.class);

    public SeatWarmerController(SeatWarmerButton passengerSeatButton,
            SeatWarmerButton driverSeatButton, HvacController hvacController) {
        mDriverSeatButton = driverSeatButton;
        mPassengerSeatButton = passengerSeatButton;

        mHvacController = hvacController;
//        mHvacController.registerCallback(mCallback);

        mDriverSeatButton.setHeatLevel(mSeatWarmerApi.getDriverSeatWarmerLevel());
        mPassengerSeatButton.setHeatLevel(mSeatWarmerApi.getPassengerSeatWarmerLevel());

        mPassengerSeatButton.setSeatWarmerClickListener(mPassengerSeatListener);
        mDriverSeatButton.setSeatWarmerClickListener(mDriverSeatListener);
        mSeatWarmerApi.registerWarmChangeCallback(this);
    }

//    private final HvacController.Callback mCallback = new HvacController.Callback() {
//        @Override
//        public void onPassengerSeatWarmerChange(int level) {
//            // If the value of the heating is less than HEAT_OFF, it means the seats are
//            // being cooled, show heated seat button as off.
//            if (level < SeatWarmerButton.HEAT_OFF) {
//                mPassengerSeatButton.setHeatLevel(SeatWarmerButton.HEAT_OFF);
//            } else {
//                mPassengerSeatButton.setHeatLevel(level);
//            }
//        }
//
//        @Override
//        public void onDriverSeatWarmerChange(int level) {
//            // If the value of the heating is less than HEAT_OFF, it means the seats are
//            // being cooled, show heated seat button as off.
//            if (level < SeatWarmerButton.HEAT_OFF) {
//                mDriverSeatButton.setHeatLevel(SeatWarmerButton.HEAT_OFF);
//            } else {
//                mDriverSeatButton.setHeatLevel(level);
//            }
//        }
//    };

    private final SeatWarmerButton.SeatWarmerButtonClickListener mPassengerSeatListener
            = new SeatWarmerButton.SeatWarmerButtonClickListener() {
        @Override
        public void onSeatWarmerButtonClicked(@SeatWarmerButton.HeatingLevel int level) {
            mSeatWarmerApi.setPassengerSeatWarmerLevel(level);
        }
    };

    private final SeatWarmerButton.SeatWarmerButtonClickListener mDriverSeatListener
            = new SeatWarmerButton.SeatWarmerButtonClickListener() {
        @Override
        public void onSeatWarmerButtonClicked(@SeatWarmerButton.HeatingLevel int level) {
            mSeatWarmerApi.setDriverSeatWarmerLevel(level);
        }
    };

    @Override
    public void onDriverLevelChange(int level) {
        // If the value of the heating is less than HEAT_OFF, it means the seats are
        // being cooled, show heated seat button as off.
        if (level < SeatWarmerButton.HEAT_OFF) {
            mDriverSeatButton.setHeatLevel(SeatWarmerButton.HEAT_OFF);
        } else {
            mDriverSeatButton.setHeatLevel(level);
        }
    }

    @Override
    public void onPassengerLevelChange(int level) {
        // If the value of the heating is less than HEAT_OFF, it means the seats are
        // being cooled, show heated seat button as off.
        if (level < SeatWarmerButton.HEAT_OFF) {
            mPassengerSeatButton.setHeatLevel(SeatWarmerButton.HEAT_OFF);
        } else {
            mPassengerSeatButton.setHeatLevel(level);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onDestroy() {
        mSeatWarmerApi.unregisterWarmChangeCallback(this);
    }
}
