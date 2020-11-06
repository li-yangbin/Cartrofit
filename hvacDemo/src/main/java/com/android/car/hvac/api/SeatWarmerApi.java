package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.HvacPanelApiId;
import com.liyangbin.cartrofit.SeatWarmerApiId;
import com.liyangbin.cartrofit.StickyType;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.annotation.UnTrack;

@Scope(value = Car.HVAC_SERVICE)
public interface SeatWarmerApi {

    @Delegate(HvacPanelApiId.setPassengerSeatWarmerLevel)
    void setPassengerSeatWarmerLevel(int level);

    @Delegate(HvacPanelApiId.setDriverSeatWarmerLevel)
    void setDriverSeatWarmerLevel(int level);

    @Inject
    void getSeatWarmLevel(@Out WarmInfo info);

    class WarmInfo {
        @Delegate(HvacPanelApiId.getDriverSeatWarmerLevel)
        public int driverLevel;

        @Delegate(HvacPanelApiId.getPassengerSeatWarmerLevel)
        public int passengerLevel;
    }

    @Register
    void registerWarmChangeCallback(OnWarmLevelChangeCallback callback);

    @UnTrack(SeatWarmerApiId.registerWarmChangeCallback)
    void unregisterWarmChangeCallback(OnWarmLevelChangeCallback callback);

    interface OnWarmLevelChangeCallback {
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.DRIVER_ZONE_ID, sticky = StickyType.ON)
        void onDriverLevelChange(int level);

        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.PASSENGER_ZONE_ID, sticky = StickyType.ON)
        void onPassengerLevelChange(int level);
    }
}
