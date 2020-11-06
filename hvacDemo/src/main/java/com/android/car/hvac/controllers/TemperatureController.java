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

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.car.hvac.HvacController;
import com.android.car.hvac.api.TemperatureApi;
import com.android.car.hvac.ui.TemperatureBarOverlay;
import com.liyangbin.cartrofit.Cartrofit;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * A controller that handles temperature updates for the driver and passenger.
 */
public class TemperatureController implements LifecycleObserver {
    private final TemperatureBarOverlay mDriverTempBarExpanded;
    private final TemperatureBarOverlay mPassengerTempBarExpanded;
    private final TemperatureBarOverlay mDriverTempBarCollapsed;
    private final TemperatureBarOverlay mPassengerTempBarCollapsed;
//    private final HvacController mHvacController;
    private final TemperatureApi mApi = Cartrofit.from(TemperatureApi.class);
    private Disposable mDisposable;

    //TODO: builder pattern for clarity
    public TemperatureController(TemperatureBarOverlay passengerTemperatureBarExpanded,
            TemperatureBarOverlay driverTemperatureBarExpanded,
            TemperatureBarOverlay passengerTemperatureBarCollapsed,
            TemperatureBarOverlay driverTemperatureBarCollapsed,
            HvacController controller) {
        mDriverTempBarExpanded = driverTemperatureBarExpanded;
        mPassengerTempBarExpanded = passengerTemperatureBarExpanded;
        mPassengerTempBarCollapsed = passengerTemperatureBarCollapsed;
        mDriverTempBarCollapsed = driverTemperatureBarCollapsed;
//        mHvacController = controller;

//        mHvacController.registerCallback(mCallback);
        mDriverTempBarExpanded.setTemperatureChangeListener(mDriverTempClickListener);
        mPassengerTempBarExpanded.setTemperatureChangeListener(mPassengerTempClickListener);

        final boolean isDriverTempControlAvailable =
                mApi.isDriverTemperatureControlAvailable();
        mDriverTempBarExpanded.setAvailable(isDriverTempControlAvailable);
        mDriverTempBarCollapsed.setAvailable(isDriverTempControlAvailable);
        if (isDriverTempControlAvailable) {
            mDriverTempBarExpanded.setTemperature(mApi.getDriverTemperature());
            mDriverTempBarCollapsed.setTemperature(mApi.getDriverTemperature());
        }

        final boolean isPassengerTempControlAvailable =
                mApi.isPassengerTemperatureControlAvailable();
        mPassengerTempBarExpanded.setAvailable(isPassengerTempControlAvailable);
        mPassengerTempBarCollapsed.setAvailable(isPassengerTempControlAvailable);
        if (isPassengerTempControlAvailable) {
            mPassengerTempBarExpanded.setTemperature(mApi.getPassengerTemperature());
            mPassengerTempBarCollapsed.setTemperature(mApi.getPassengerTemperature());
        }

        mDisposable = mApi.trackTempChange().subscribe(new Consumer<TemperatureApi.TempInfo>() {
            @Override
            public void accept(TemperatureApi.TempInfo tempInfo) throws Exception {
                mDriverTempBarCollapsed.setTemperature(tempInfo.driverTemp);
                mDriverTempBarExpanded.setTemperature(tempInfo.driverTemp);

                mPassengerTempBarExpanded.setTemperature(tempInfo.passengerTemp);
                mPassengerTempBarCollapsed.setTemperature(tempInfo.passengerTemp);
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onDestroy() {
        mDisposable.dispose();
    }

//    private final HvacController.Callback mCallback = new HvacController.Callback() {
//        @Override
//        public void onPassengerTemperatureChange(CarPropertyValue value) {
//            final boolean available = value.getStatus() == CarPropertyValue.STATUS_AVAILABLE;
//            mPassengerTempBarExpanded.setAvailable(available);
//            mPassengerTempBarCollapsed.setAvailable(available);
//            if (available) {
//                final int temp = ((Float)value.getValue()).intValue();
//                mPassengerTempBarExpanded.setTemperature(temp);
//                mPassengerTempBarCollapsed.setTemperature(temp);
//            }
//        }
//
//        @Override
//        public void onDriverTemperatureChange(CarPropertyValue value) {
//            final boolean available = value.getStatus() == CarPropertyValue.STATUS_AVAILABLE;
//            mDriverTempBarExpanded.setAvailable(available);
//            mDriverTempBarExpanded.setAvailable(available);
//            if (available) {
//                final int temp = ((Float)value.getValue()).intValue();
//                mDriverTempBarCollapsed.setTemperature(temp);
//                mDriverTempBarExpanded.setTemperature(temp);
//            }
//        }
//    };

    private final TemperatureBarOverlay.TemperatureAdjustClickListener mPassengerTempClickListener =
            new TemperatureBarOverlay.TemperatureAdjustClickListener() {
                @Override
                public void onTemperatureChanged(int temperature) {
                    mApi.setPassengerTemperature(temperature);
                    mPassengerTempBarCollapsed.setTemperature(temperature);
                }
            };

    private final TemperatureBarOverlay.TemperatureAdjustClickListener mDriverTempClickListener =
            new TemperatureBarOverlay.TemperatureAdjustClickListener() {
                @Override
                public void onTemperatureChanged(int temperature) {
                    mApi.setDriverTemperature(temperature);
                    mDriverTempBarCollapsed.setTemperature(temperature);
                }
            };
}
