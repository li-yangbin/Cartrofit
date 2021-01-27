package com.liyangbin.cartrofit;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;

public interface CarPropertyConfigAccess {
    CarPropertyConfig<?> getConfig(int propertyId, int area) throws CarNotConnectedException;
}
