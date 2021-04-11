package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.carproperty.CarPropertyScope;
import com.liyangbin.cartrofit.carproperty.Set;
import com.liyangbin.cartrofit.carproperty.Track;

@CarPropertyScope(Car.HVAC_SERVICE)
public interface FanDirectionApi {
    @Set(propId = CarHvacManager.ID_ZONED_FAN_DIRECTION, area = HvacPanelApi.SEAT_ALL)
    void setFanDirection(int state);

    @Track(propId = CarHvacManager.ID_ZONED_FAN_DIRECTION, area = HvacPanelApi.SEAT_ALL)
    void trackFanDirection(@Callback OnDanDirectionChangCallback callback);

    interface OnDanDirectionChangCallback {
        void onChange(int state);
    }
}
