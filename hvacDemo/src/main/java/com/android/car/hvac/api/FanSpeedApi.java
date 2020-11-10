package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.android.car.hvac.controllers.FanSpeedBarController;
import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.UnTrack;

@GenerateId
@Scope(value = Car.HVAC_SERVICE)
public interface FanSpeedApi {

    int MAX_FAN_SPEED = 6;
    int MIN_FAN_SPEED = 1;

    @Set(id = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL)
    void setFanSpeed(int fanSpeed);

    @Set(id = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL, value = @CarValue(Int = MAX_FAN_SPEED))
    void setFanSpeedToMax();

    @Set(id = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = HvacPanelApi.SEAT_ALL, value = @CarValue(Int = MIN_FAN_SPEED))
    void setFanSpeedToMin();

    @Register
    void registerFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);

    @UnTrack(FanSpeedApiId.registerFanSpeedChangeCallback)
    void unregisterFanSpeedChangeCallback(FanSpeedBarController.OnFanSpeedChangeCallback callback);
}
