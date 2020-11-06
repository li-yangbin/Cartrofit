package com.android.car.hvac.api;

import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaWindow;
import android.car.hardware.hvac.CarHvacManager;

import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableInt;
import androidx.lifecycle.LiveData;

import com.liyangbin.cartrofit.StickyType;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;

@Scope(value = Car.HVAC_SERVICE, publish = true)
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

    @Set(id = CarHvacManager.ID_ZONED_HVAC_POWER_ON, area = SEAT_ALL)
    void setHvacPowerState(boolean onOff);

    @Get(id = CarHvacManager.ID_ZONED_HVAC_POWER_ON, area = SEAT_ALL)
    boolean getHvacPowerState();

    @Set(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
    void setDriverSeatWarmerLevel(int level);

    @Set(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
    void setPassengerSeatWarmerLevel(int level);

    @Get(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
    int getDriverSeatWarmerLevel();

    @Get(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
    int getPassengerSeatWarmerLevel();

    @Set(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD)
    void setFrontDefrosterState(boolean onOff);

    @Track(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD, sticky = StickyType.ON)
    LiveData<Boolean> trackFrontDefrosterState();

    @Set(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_REAR_WINDSHIELD)
    void setRearDefrosterState(boolean onOff);

    @Track(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_REAR_WINDSHIELD, sticky = StickyType.ON)
    LiveData<Boolean> trackRearDefrosterState();

    @Set(id = CarHvacManager.ID_ZONED_AC_ON, area = SEAT_ALL)
    void setACState(boolean onOff);

    @Track(id = CarHvacManager.ID_ZONED_AC_ON, area = SEAT_ALL, sticky = StickyType.ON)
    ObservableBoolean trackACState();

    @Set(id = CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, area = SEAT_ALL)
    void setAirCirculation(boolean state);

    @Track(id = CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, area = SEAT_ALL, sticky = StickyType.ON)
    LiveData<Boolean> trackAirCirculation();

    @Set(id = CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON, area = SEAT_ALL)
    void setAutoModeState(boolean state);

    @Get(id = CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON, area = SEAT_ALL)
    boolean getAutoModeState();
}
