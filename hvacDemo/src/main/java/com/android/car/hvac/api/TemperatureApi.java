package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.annotation.Process;
import com.liyangbin.cartrofit.carproperty.Availability;
import com.liyangbin.cartrofit.carproperty.CarPropertyScope;
import com.liyangbin.cartrofit.carproperty.Get;
import com.liyangbin.cartrofit.carproperty.Set;
import com.liyangbin.cartrofit.carproperty.Track;

import io.reactivex.Observable;

@Process
@CarPropertyScope(Car.HVAC_SERVICE)
public interface TemperatureApi {

    @Set(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    void setDriverTemperature(float temperature);

    @Get(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    float getDriverTemperature();

    @Set(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    void setPassengerTemperature(float temperature);

    @Get(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    float getPassengerTemperature();

    @Track(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    Observable<Float> trackPassengerTemperature();

    @Availability
    @Get(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    boolean isDriverTemperatureControlAvailable();

    @Track(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    Observable<Float> trackDriverTemperature();

    @Availability
    @Get(propId = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    boolean isPassengerTemperatureControlAvailable();
}