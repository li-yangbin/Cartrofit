package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.android.car.hvac.controllers.FanSpeedBarController;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Process;
import com.liyangbin.cartrofit.annotation.Unregister;
import com.liyangbin.cartrofit.carproperty.CarPropertyScope;
import com.liyangbin.cartrofit.carproperty.Set;
import com.liyangbin.cartrofit.carproperty.Track;

@Process
@CarPropertyScope(Car.HVAC_SERVICE)
public interface FanSpeedApi {

    int MAX_FAN_SPEED = 6;
    int MIN_FAN_SPEED = 1;

    @Set(propId = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL)
    void setFanSpeed(int fanSpeed);

    @Track(propId = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL)
    void registerFanSpeedChangeCallback(@Callback FanSpeedBarController.OnFanSpeedChangeCallback callback);

    @Unregister(FanSpeedApiId.registerFanSpeedChangeCallback)
    void unregisterFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);
}
