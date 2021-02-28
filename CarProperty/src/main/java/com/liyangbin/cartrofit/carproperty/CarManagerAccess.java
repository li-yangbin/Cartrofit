package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;

public interface CarManagerAccess<CAR> {
    void tryConnect();
    CAR get() throws CarNotConnectedException;
    boolean isAvailable();
    void addOnCarAvailabilityListener(CarAvailabilityListener listener);
}