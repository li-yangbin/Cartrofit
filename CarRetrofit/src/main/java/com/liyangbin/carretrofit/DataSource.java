package com.liyangbin.carretrofit;

import android.car.hardware.CarPropertyValue;

public interface DataSource {
    Object get(int key, int area, CarType type) throws Exception;

    <TYPE> void set(int key, int area, TYPE value) throws Exception;

    Flow<CarPropertyValue<?>> track(int key, int area) throws Exception;

    Class<?> extractValueType(int key) throws Exception;

    void onCommandCreate(Command command);
}
