package com.liyangbin.carretrofit;

import android.car.hardware.CarPropertyValue;

public interface DataSource extends ApiCallback {
    Object get(int key, int area, CarType type);

    <TYPE> void set(int key, int area, TYPE value);

    Flow<CarPropertyValue<?>> track(int key, int area);

    Class<?> extractValueType(int key);

    String getScopeId();
}
