package com.android.car.hvac.api;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaWindow;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.carproperty.CarPropertyScope;
import com.liyangbin.cartrofit.carproperty.Get;
import com.liyangbin.cartrofit.carproperty.Set;
import com.liyangbin.cartrofit.carproperty.Track;

import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.LiveData;

@GenerateId
@CarPropertyScope(Car.HVAC_SERVICE)
public interface HvacPanelApi {
    int DRIVER_ZONE_ID = VehicleAreaSeat.SEAT_ROW_1_LEFT |
            VehicleAreaSeat.SEAT_ROW_2_LEFT | VehicleAreaSeat.SEAT_ROW_2_CENTER;
    int PASSENGER_ZONE_ID = VehicleAreaSeat.SEAT_ROW_1_RIGHT |
            VehicleAreaSeat.SEAT_ROW_2_RIGHT;

    int[] AIRFLOW_STATES = new int[]{
            CarHvacManager.FAN_DIRECTION_FACE,
            CarHvacManager.FAN_DIRECTION_FLOOR,
            (CarHvacManager.FAN_DIRECTION_FACE | CarHvacManager.FAN_DIRECTION_FLOOR)
    };
    // Hardware specific value for the front seats
    int SEAT_ALL = VehicleAreaSeat.SEAT_ROW_1_LEFT |
            VehicleAreaSeat.SEAT_ROW_1_RIGHT | VehicleAreaSeat.SEAT_ROW_2_LEFT |
            VehicleAreaSeat.SEAT_ROW_2_CENTER | VehicleAreaSeat.SEAT_ROW_2_RIGHT;

    @Set(propId = CarHvacManager.ID_ZONED_HVAC_POWER_ON, area = SEAT_ALL)
    void setHvacPowerState(boolean onOff) throws CarNotConnectedException;

    @Get(propId = CarHvacManager.ID_ZONED_HVAC_POWER_ON, area = SEAT_ALL)
    boolean getHvacPowerState();

    @Set(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
    void setDriverSeatWarmerLevel(float level);

    @Set(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
    void setPassengerSeatWarmerLevel(float level);

    @Get(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
    int getDriverSeatWarmerLevel();

    @Get(propId = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
    int getPassengerSeatWarmerLevel();

    @Set(propId = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD)
    void setFrontDefrosterState(boolean onOff);

    @Track(propId = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD)
    LiveData<Boolean> trackFrontDefrosterState();

    @Set(propId = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_REAR_WINDSHIELD)
    void setRearDefrosterState(boolean onOff);

    @Track(propId = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_REAR_WINDSHIELD)
    LiveData<Boolean> trackRearDefrosterState();

    @Set(propId = CarHvacManager.ID_ZONED_AC_ON, area = SEAT_ALL)
    void setACState(boolean onOff);

    @Track(propId = CarHvacManager.ID_ZONED_AC_ON, area = SEAT_ALL)
    ObservableBoolean trackACState();

    @Set(propId = CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, area = SEAT_ALL)
    void setAirCirculation(boolean state);

    @Track(propId = CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, area = SEAT_ALL, restoreIfTimeout = true)
    LiveData<Boolean> trackAirCirculation();

    @Set(propId = CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON, area = SEAT_ALL)
    void setAutoModeState(boolean state);

    @Get(propId = CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON, area = SEAT_ALL)
    boolean getAutoModeState();
}
