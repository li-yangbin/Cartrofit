package com.liyangbin.cartrofit.carproperty.context;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarVendorExtensionManager;
import android.content.Context;

import com.liyangbin.cartrofit.carproperty.CarPropertyAccess;
import com.liyangbin.cartrofit.carproperty.CarPropertyContext;
import com.liyangbin.cartrofit.carproperty.CarServiceAccess;
import com.liyangbin.cartrofit.carproperty.DefaultCarServiceAccess;

import java.util.List;

public class VendorExtensionContext extends CarPropertyContext<CarVendorExtensionManager>
        implements CarVendorExtensionManager.CarVendorExtensionCallback {

    public VendorExtensionContext(Context context) {
        this(new DefaultCarServiceAccess<>(context, Car.VENDOR_EXTENSION_SERVICE));
    }

    public VendorExtensionContext(CarServiceAccess<CarVendorExtensionManager> access) {
        super(access);
    }

    @Override
    public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
        return getManagerLazily().getProperties();
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
        return getManagerLazily().isPropertyAvailable(propertyId, area);
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
    public CarPropertyAccess<?> getCarPropertyAccess(Class<?> type) {
        return new CarPropertyAccess<Object>() {
            @Override
            public Object get(int propertyId, int area) throws CarNotConnectedException {
                return getManagerLazily().getProperty(type, propertyId, area);
            }

            @Override
            public void set(int propertyId, int area, Object value) throws CarNotConnectedException {
                getManagerLazily().setProperty((Class<Object>)type, propertyId, area, value);
            }
        };
    }
}
