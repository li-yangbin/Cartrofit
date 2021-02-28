package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;

public interface CarPropertyAccess<T> {
    T get(int propertyId, int area) throws CarNotConnectedException;

    void set(int propertyId, int area, T value) throws CarNotConnectedException;
}
