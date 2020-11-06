package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import androidx.databinding.ObservableInt;

import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;

@Scope(value = Car.HVAC_SERVICE)
public interface FanDirectionApi {
    @Set(id = CarHvacManager.ID_ZONED_FAN_DIRECTION, area = HvacPanelApi.SEAT_ALL)
    void setFanDirection(int state);

    @Track(id = CarHvacManager.ID_ZONED_FAN_DIRECTION, area = HvacPanelApi.SEAT_ALL)
    ObservableInt trackFanDirection();
}
