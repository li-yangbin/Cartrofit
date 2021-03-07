package com.liyangbin.cartrofit.carproperty.context;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.cabin.CarCabinManager;
import android.content.Context;

import com.liyangbin.cartrofit.carproperty.CarPropertyAccess;
import com.liyangbin.cartrofit.carproperty.CarPropertyContext;
import com.liyangbin.cartrofit.carproperty.DefaultCarServiceAccess;

import java.util.List;

public class CabinContext extends CarPropertyContext<CarCabinManager> implements
        CarCabinManager.CarCabinEventCallback {

    public CabinContext(Context context) {
        super(new DefaultCarServiceAccess<>(context, Car.CABIN_SERVICE));
    }

    @Override
    public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
        return getManagerLazily().getPropertyList();
    }

    @Override
    public void onGlobalRegister(boolean register) throws CarNotConnectedException {
        if (register) {
            getManagerLazily().registerCallback(this);
        } else {
            getManagerLazily().unregisterCallback(this);
        }
    }

    @Override
    public boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException {
        throw new UnsupportedOperationException("Cabin is not support availability");
    }

    @Override
    public void onChangeEvent(CarPropertyValue carPropertyValue) {
        send(carPropertyValue);
    }

    @Override
    public void onErrorEvent(int propertyId, int area) {
        error(propertyId, area);
    }

    @Override
    public CarPropertyAccess<Integer> getIntCarPropertyAccess() {
        return new CarPropertyAccess<Integer>() {
            @Override
            public Integer get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getIntProperty(propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                getManagerLazily().setIntProperty(propertyId, area, value);
            }
        };
    }

    @Override
    public CarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
        return new CarPropertyAccess<Boolean>() {
            @Override
            public Boolean get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getBooleanProperty(propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Boolean value) throws CarNotConnectedException {
                getManagerLazily().setBooleanProperty(propertyId, area, value);
            }
        };
    }

    @Override
    public CarPropertyAccess<Float> getFloatCarPropertyAccess() {
        return new CarPropertyAccess<Float>() {
            @Override
            public Float get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getFloatProperty(propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Float value) throws CarNotConnectedException {
                getManagerLazily().setFloatProperty(propertyId, area, value);
            }
        };
    }
}
