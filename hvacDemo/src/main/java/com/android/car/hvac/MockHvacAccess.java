package com.android.car.hvac;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.hvac.CarHvacManager;
import android.content.Context;
import android.os.Handler;

import com.liyangbin.cartrofit.carproperty.CarAvailabilityListener;
import com.liyangbin.cartrofit.carproperty.CarServiceAccess;

public class MockHvacAccess implements CarServiceAccess<CarHvacManager> {

    private final CarHvacManager mockManager;

    public MockHvacAccess(Context context) {
        mockManager = new CarHvacManager(new LocalHvacPropertyService().getCarPropertyService(),
                context, new Handler());
    }

    @Override
    public void tryConnect() {
    }

    @Override
    public String getKey() {
        return Car.HVAC_SERVICE;
    }

    @Override
    public CarHvacManager get() throws CarNotConnectedException {
        return mockManager;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void addOnCarAvailabilityListener(CarAvailabilityListener listener) {
    }

    @Override
    public void removeOnCarAvailabilityListener(CarAvailabilityListener listener) {
    }
}
