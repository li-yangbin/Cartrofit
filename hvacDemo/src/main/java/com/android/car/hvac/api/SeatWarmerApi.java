package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.Process;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Unregister;
import com.liyangbin.cartrofit.carproperty.CarPropertyScope;
import com.liyangbin.cartrofit.carproperty.Track;

@Process
@CarPropertyScope(Car.HVAC_SERVICE)
public interface SeatWarmerApi {

    String FLOAT_TO_INT = "float2int";
    String INT_TO_FLOAT = "int2float";

    @Delegate(HvacPanelApiId.setPassengerSeatWarmerLevel)
    void setPassengerSeatWarmerLevel(int level);

    @Delegate(HvacPanelApiId.setDriverSeatWarmerLevel)
    void setDriverSeatWarmerLevel(int level);

    @Delegate(HvacPanelApiId.getDriverSeatWarmerLevel)
    int getDriverSeatWarmerLevel();

    @Delegate(HvacPanelApiId.getPassengerSeatWarmerLevel)
    int getPassengerSeatWarmerLevel();

    @Register
    void registerWarmChangeCallback(@Callback OnWarmLevelChangeCallback callback);

    @Unregister(SeatWarmerApiId.registerWarmChangeCallback)
    void unregisterWarmChangeCallback(OnWarmLevelChangeCallback callback);

    interface OnWarmLevelChangeCallback {
        @Track(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.DRIVER_ZONE_ID)
        void onDriverLevelChange(int level);

        @Track(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.PASSENGER_ZONE_ID)
        void onPassengerLevelChange(int level);
    }
}