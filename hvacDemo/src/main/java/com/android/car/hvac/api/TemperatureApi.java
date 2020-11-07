package com.android.car.hvac.api;

import android.car.Car;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.ApiBuilder;
import com.liyangbin.cartrofit.ApiCallback;
import com.liyangbin.cartrofit.CarType;
import com.liyangbin.cartrofit.Converter;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.TwoWayConverter;
import com.liyangbin.cartrofit.annotation.Combine;
import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;

import io.reactivex.Observable;

import static com.android.car.hvac.api.TemperatureApiId.*;

@GenerateId
@Scope(value = Car.HVAC_SERVICE, onCreate = CreateHelper.class)
public interface TemperatureApi extends ApiCallback {

    @Set(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    void setDriverTemperature(int temperature);

    @Get(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    int getDriverTemperature();

    @Track(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID)
    Flow<Integer> trackDriverTemperature();

    @Set(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    void setPassengerTemperature(int temperature);

    @Get(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    int getPassengerTemperature();

    @Track(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID)
    Observable<Integer> trackPassengerTemperature();

    @Get(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.DRIVER_ZONE_ID, type = CarType.AVAILABILITY)
    boolean isDriverTemperatureControlAvailable();

    @Get(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = HvacPanelApi.PASSENGER_ZONE_ID, type = CarType.AVAILABILITY)
    boolean isPassengerTemperatureControlAvailable();

    @Combine(elements = {TemperatureApiId.trackDriverTemperature, TemperatureApiId.trackPassengerTemperature})
    Observable<TempInfo> trackTempChange();

    class TempInfo {
        public int driverTemp;
        public int passengerTemp;

        public TempInfo(int driverTemp, int passengerTemp) {
            this.driverTemp = driverTemp;
            this.passengerTemp = passengerTemp;
        }
    }
}

class CreateHelper implements ApiCallback {
    @Override
    public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
        builder.combine(int.class, int.class)
                .to(TemperatureApi.TempInfo.class)
                .by(TemperatureApi.TempInfo::new)
                .apply(trackTempChange);

        builder.convert(Float.class)
                .to(int.class)
                .by(new TwoWayConverter<Float, Integer>() {
                    @Override
                    public Integer fromCar2App(Float value) {
                        return value.intValue();
                    }

                    @Override
                    public Float fromApp2Car(Integer integer) {
                        return integer.floatValue();
                    }
                })
                .apply(getDriverTemperature, getPassengerTemperature,
                        setPassengerTemperature, setDriverTemperature,
                        trackDriverTemperature, trackPassengerTemperature);
    }
}
