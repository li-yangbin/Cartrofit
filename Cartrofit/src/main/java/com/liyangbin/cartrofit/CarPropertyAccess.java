package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.flow.Flow;

public interface CarPropertyAccess<T> {
    CarPropertyValue<T> getValue(int propertyId, int area);

    T get(int propertyId, int area);

    boolean isPropertyAvailable(int propertyId, int area);

    void set(int propertyId, int area, T value);

    Flow<CarPropertyValue<T>> track(int propertyId, int area);
}
