package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.ApiBuilder;
import com.liyangbin.cartrofit.ApiCallback;
import com.liyangbin.cartrofit.Constraint;
import com.liyangbin.cartrofit.StickyType;
import com.liyangbin.cartrofit.annotation.Category;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Inject;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.annotation.UnTrack;

@GenerateId
@Scope(value = Car.HVAC_SERVICE, onCreate = SeatWarmerApiCreateHelper.class)
public interface SeatWarmerApi {

    String FLOAT_TO_INT = "float2int";

    @Delegate(HvacPanelApiId.setPassengerSeatWarmerLevel)
    void setPassengerSeatWarmerLevel(int level);

    @Delegate(HvacPanelApiId.setDriverSeatWarmerLevel)
    void setDriverSeatWarmerLevel(int level);

    @Inject
    void getSeatWarmLevel(@Out WarmInfo info);

    class WarmInfo {
        @Category(FLOAT_TO_INT)
        @Delegate(HvacPanelApiId.getDriverSeatWarmerLevel)
        public int driverLevel;

        @Category(FLOAT_TO_INT)
        @Delegate(HvacPanelApiId.getPassengerSeatWarmerLevel)
        public int passengerLevel;
    }

    @Register
    void registerWarmChangeCallback(OnWarmLevelChangeCallback callback);

    @UnTrack(SeatWarmerApiId.registerWarmChangeCallback)
    void unregisterWarmChangeCallback(OnWarmLevelChangeCallback callback);

    interface OnWarmLevelChangeCallback {
        @Category(FLOAT_TO_INT)
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.DRIVER_ZONE_ID, sticky = StickyType.ON)
        void onDriverLevelChange(int level);

        @Category(FLOAT_TO_INT)
        @Track(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = HvacPanelApi.PASSENGER_ZONE_ID, sticky = StickyType.ON)
        void onPassengerLevelChange(int level);
    }
}

class SeatWarmerApiCreateHelper implements ApiCallback {
    @Override
    public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
        builder.convert(float.class)
                .to(int.class)
                .by(Float::intValue)
                .apply(Constraint.of(SeatWarmerApi.FLOAT_TO_INT));
    }
}
