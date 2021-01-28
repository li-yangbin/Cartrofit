package com.liyangbin.cartrofit;

import android.car.CarNotConnectedException;

public interface TypedCarPropertyAccess<T> {
    T get(int propertyId, int area) throws CarNotConnectedException;

    void set(int propertyId, int area, T value) throws CarNotConnectedException;
}
