package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.android.car.hvac.controllers.FanSpeedBarController;
import com.liyangbin.cartrofit.FanSpeedApiId;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.UnTrack;

@Scope(value = Car.HVAC_SERVICE, publish = true)
public interface FanSpeedApi {

    @Set(id = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL)
    void setFanSpeed(int fanSpeed);

    @Register
    void registerFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);

    @UnTrack(FanSpeedApiId.registerFanSpeedChangeCallback)
    void unregisterFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);
}
