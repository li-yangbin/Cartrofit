package com.android.car.hvac;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyManager;

import com.liyangbin.carretrofit.CarType;
import com.liyangbin.carretrofit.Command;
import com.liyangbin.carretrofit.DataSource;
import com.liyangbin.carretrofit.Flow;

import java.util.List;

public class HvacDataSource implements DataSource {

    CarPropertyManager mCarPropertyManager;
    HvacPolicy mPolicy;

    @Override
    public Object get(int key, int area, CarType type) throws Exception {
        switch (type) {
            case VALUE:
                return mCarPropertyManager.getProperty(key, area).getValue();
            case AVAILABILITY:
                boolean result = mManager.isPropertyAvailable(key, area);
                return Boolean.valueOf(result);
            case CONFIG:
                if (mConfigMap.containsKey(key)) {
                    return mConfigMap.get(key);
                }
                List<CarPropertyConfig> list = mManager.getPropertyList();
                if (list != null) {
                    list.forEach(carPropertyConfig ->
                            mConfigMap.put(carPropertyConfig.getPropertyId(), carPropertyConfig));
                }
                return mConfigMap.get(key);
            case ALL:
            default:
                throw new IllegalArgumentException("Can not call get() with " + type + " type");
        }
    }

    @Override
    public <TYPE> void set(int key, int area, TYPE value) throws Exception {

    }

    @Override
    public Flow<CarPropertyValue<?>> track(int key, int area) throws Exception {
        return null;
    }

    @Override
    public Class<?> extractValueType(int key) throws Exception {
        return null;
    }

    @Override
    public void onCommandCreate(Command command) {

    }
}
