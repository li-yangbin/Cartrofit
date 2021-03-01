package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;

public interface CarServiceAccess<CAR_MANAGER> {
    void tryConnect();
    CAR_MANAGER get() throws CarNotConnectedException;
    boolean isAvailable();
    void addOnCarAvailabilityListener(CarAvailabilityListener listener);
}