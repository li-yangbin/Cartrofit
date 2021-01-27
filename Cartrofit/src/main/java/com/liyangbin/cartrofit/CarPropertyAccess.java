package com.liyangbin.cartrofit;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.flow.Flow;

public interface CarPropertyAccess<T> {
    CarPropertyValue<T> getValue(int propertyId, int area) throws CarNotConnectedException;

    T get(int propertyId, int area) throws CarNotConnectedException;

    boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException;

    void set(int propertyId, int area, T value) throws CarNotConnectedException;

    Flow<CarPropertyValue<T>> track(int propertyId, int area) throws CarNotConnectedException;
}
