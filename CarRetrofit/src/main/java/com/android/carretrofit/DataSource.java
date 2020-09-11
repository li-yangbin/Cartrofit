package com.android.carretrofit;

import android.car.hardware.CarPropertyValue;

public interface DataSource {
    <VALUE> VALUE get(int key, int area, CarType type) throws Exception;

    <VALUE> void set(int key, int area, VALUE value) throws Exception;

    Flow<CarPropertyValue<?>> track(int key, int area) throws Exception;

    <VALUE> Class<VALUE> extractValueType(int key) throws Exception;
}
