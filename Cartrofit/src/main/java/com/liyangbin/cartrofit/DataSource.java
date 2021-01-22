package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.flow.Flow;

public interface DataSource {
    Object get(int key, int area, CarType type);

    <TYPE> void set(int key, int area, TYPE value);

    Flow<CarPropertyValue<?>> track(int key, int area);

    Class<?> extractValueType(int key);
}
