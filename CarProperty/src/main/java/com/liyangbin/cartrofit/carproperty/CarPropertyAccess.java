package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.flow.Flow;

public interface CarPropertyAccess {
    CarPropertyConfig<?> getConfig(int propertyId, int area) throws CarNotConnectedException;

    Flow<CarPropertyValue<?>> track(int propertyId, int area) throws CarNotConnectedException;

    boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException;
}
