package com.android.car.hvac;

import android.car.VehicleAreaSeat;
import android.car.VehicleAreaWindow;
import android.car.hardware.hvac.CarHvacManager;

import com.liyangbin.carretrofit.Command;
import com.liyangbin.carretrofit.Interceptor;
import com.liyangbin.carretrofit.annotation.CarApi;
import com.liyangbin.carretrofit.annotation.Set;

@CarApi
public interface HvacApi {
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

    @Set(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = DRIVER_ZONE_ID)
    void setDriverTemperature(int temperature);

    @Set(id = CarHvacManager.ID_ZONED_TEMP_SETPOINT, area = PASSENGER_ZONE_ID)
    void setPassengerTemperature(int temperature);

    @Set(id = CarHvacManager.ID_ZONED_HVAC_POWER_ON, area = SEAT_ALL)
    void setPowerState(boolean onOff);

    @Set(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = DRIVER_ZONE_ID)
    void setDriverSeatWarmerLevel(int level);

    @Set(id = CarHvacManager.ID_ZONED_SEAT_TEMP, area = PASSENGER_ZONE_ID)
    void setPassengerSeatWarmerLevel(int level);

    @Set(id = CarHvacManager.ID_ZONED_FAN_SPEED_SETPOINT, area = SEAT_ALL)
    void setFanSpeed(int fanSpeed);

    @Set(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD)
    void setFrontDefrosterState(boolean onOff);

    @Set(id = CarHvacManager.ID_WINDOW_DEFROSTER_ON, area = VehicleAreaWindow.WINDOW_REAR_WINDSHIELD)
    void setRearDefrosterState(boolean onOff);

    @Set(id = CarHvacManager.ID_ZONED_AC_ON, area = SEAT_ALL)
    void setACState(boolean onOff);

    @Set(id = CarHvacManager.ID_ZONED_FAN_DIRECTION, area = SEAT_ALL)
    void setFanDirection(boolean onOff);

    @Set(id = CarHvacManager.ID_ZONED_AIR_RECIRCULATION_ON, area = SEAT_ALL)
    void setAirCirculation(boolean state);

    @Set(id = CarHvacManager.ID_ZONED_AUTOMATIC_MODE_ON, area = SEAT_ALL)
    void setAutoMode(boolean state);
}
