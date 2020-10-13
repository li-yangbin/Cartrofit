package com.android.car.hvac;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.SparseLongArray;

import com.liyangbin.carretrofit.CarType;
import com.liyangbin.carretrofit.Command;
import com.liyangbin.carretrofit.Converter;
import com.liyangbin.carretrofit.DataSource;
import com.liyangbin.carretrofit.Flow;
import com.liyangbin.carretrofit.HvacApiId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class HvacDataSource implements DataSource {

    private CarPropertyManager mCarPropertyManager;
    private CarHvacManager mCarHvacManager;
    private HvacPolicy mPolicy;
    private SparseArray<CarPropertyConfig<?>> mConfigMap = new SparseArray<>();
    private boolean mHvacChangeTracked;
    private final SparseArray<AreaPool> mPublishedFlowList = new SparseArray<>();
    private final SparseLongArray mLastSetMillis = new SparseLongArray();
    private Handler mHandler = new Handler();

    private HvacDataSource(Car car, Context context) {
        try {
            mCarPropertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            mCarHvacManager = (CarHvacManager) car.getCarManager(Car.HVAC_SERVICE);
        } catch (CarNotConnectedException ce) {
            throw new RuntimeException(ce);
        }
        List<CarPropertyConfig> list = null;
        try {
            list = mCarHvacManager.getPropertyList();
        } catch (CarNotConnectedException e) {
            e.printStackTrace();
        }
        if (list != null) {
            list.forEach(carPropertyConfig ->
                    mConfigMap.put(carPropertyConfig.getPropertyId(), carPropertyConfig));
        }
        mPolicy = new HvacPolicy(context, list);
    }

    @Override
    public Object get(int key, int area, CarType type) throws Exception {
        switch (type) {
            case VALUE:
                return mCarPropertyManager.getProperty(key, area).getValue();
            case AVAILABILITY:
                return mCarPropertyManager.isPropertyAvailable(key, area);
            case CONFIG:
                return mConfigMap.get(key);
            case ALL:
            default:
                throw new IllegalArgumentException("Can not call get() with " + type + " type");
        }
    }

    @Override
    public <TYPE> void set(int key, int area, TYPE value) throws Exception {
        mCarPropertyManager.setProperty((Class<? super TYPE>) extractValueType(key), key, area, value);
    }

    @Override
    public Flow<CarPropertyValue<?>> track(int key, int area) throws Exception {
        if (!mHvacChangeTracked) {
            mCarHvacManager.registerCallback(new CarHvacManager.CarHvacEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    notifyChange(value);
                }

                @Override
                public void onErrorEvent(int i, int i1) {
                }
            });
            mHvacChangeTracked = true;
        }
        synchronized (mPublishedFlowList) {
            AreaPool pool = mPublishedFlowList.get(key);
            if (pool == null) {
                pool = new AreaPool(key);
                mPublishedFlowList.put(key, pool);
            }
            return pool.obtainChildArea(area);
        }
    }

    public void notifyChange(CarPropertyValue<?> value) {
        synchronized (mPublishedFlowList) {
            AreaPool areaPool = mPublishedFlowList.get(value.getPropertyId());
            if (areaPool != null) {
                areaPool.notifyAreaChange(value);
            }
        }
    }

    private class AreaPool {
        int key;
        ArrayList<SimpleFlow> childFlow = new ArrayList<>();

        public AreaPool(int key) {
            this.key = key;
        }

        SimpleFlow obtainChildArea(int area) {
            for (int i = 0; i < childFlow.size(); i++) {
                SimpleFlow flow = childFlow.get(i);
                if (flow.areaId == area) {
                    return flow;
                }
            }
            SimpleFlow flow = new SimpleFlow(key, area);
            childFlow.add(flow);
            return flow;
        }

        void notifyAreaChange(CarPropertyValue<?> carValue) {
            for (int i = 0; i < childFlow.size(); i++) {
                SimpleFlow flow = childFlow.get(i);
                if (flow.areaId == 0 || carValue.getAreaId() == 0
                        || (flow.areaId & carValue.getAreaId()) != 0) {
                    flow.publishChange(carValue);
                }
            }
        }
    }

    private class SimpleFlow implements Flow<CarPropertyValue<?>> {

        private ArrayList<Consumer<CarPropertyValue<?>>> consumerList = new ArrayList<>();
        CarPropertyValue<?> value;
        private int propertyId;
        private int areaId;

        SimpleFlow(int propertyId, int areaId) {
            this.propertyId = propertyId;
            this.areaId = areaId;
        }

        @Override
        public void addObserver(Consumer<CarPropertyValue<?>> consumer) {
            consumerList.add(Objects.requireNonNull(consumer));
        }

        @Override
        public void removeObserver(Consumer<CarPropertyValue<?>> consumer) {
            consumerList.remove(Objects.requireNonNull(consumer));
        }

        void publishChange(CarPropertyValue<?> value) {
            this.value = value;
            if (consumerList.size() > 0) {
                ArrayList<Consumer<CarPropertyValue<?>>> consumers
                        = (ArrayList<Consumer<CarPropertyValue<?>>>) consumerList.clone();
                for (int i = 0; i < consumers.size(); i++) {
                    consumers.get(i).accept(this.value);
                }
            }
        }
    }

    @Override
    public Class<?> extractValueType(int key) throws Exception {
        return mConfigMap.get(key).getPropertyType();
    }

    @Override
    public void onCommandCreate(Command command) {
        installInterceptor(command);
        installConverter(command);
    }

    private void installInterceptor(Command command) {
        switch (command.type()) {
            case SET:
                command.addInterceptorToTop((target, parameter) -> {
                    AsyncTask.execute(() -> {
                        try {
                            target.invoke(parameter);
                            synchronized (mLastSetMillis) {
                                mLastSetMillis.put(target.getPropertyId(), SystemClock.uptimeMillis());
                            }
                        } catch (Throwable throwable) {
                            throw new RuntimeException(throwable);
                        }
                    });
                    return null;
                });
            case RECEIVE:
                command.addInterceptorToTop((target, parameter) -> {
                    long lastSetMillis;
                    synchronized (mLastSetMillis) {
                        lastSetMillis = mLastSetMillis.get(target.getPropertyId());
                    }
                    if (SystemClock.uptimeMillis() - lastSetMillis < 500) {
                        mHandler.post(() -> {
                            try {
                                target.invoke(parameter);
                            } catch (Throwable throwable) {
                                throw new RuntimeException(throwable);
                            }
                        });
                    }
                    return null;
                });
        }
    }

    private void installConverter(Command command) {
        switch (command.getId()) {
            case HvacApiId.setDriverTemperature:
            case HvacApiId.setPassengerTemperature:
                command.setConverter((Converter<Integer, Float>) value -> mPolicy.userToHardwareTemp(value));
                break;
            case HvacApiId.getDriverTemperature:
            case HvacApiId.getPassengerTemperature:
                command.setConverter((Converter<Float, Integer>) value -> mPolicy.hardwareToUserTemp(value));
                break;
        }
    }

    private interface IntConverter extends Converter<Integer, Integer> {
        @Override
        default Integer convert(Integer value) {
            return convert(value.intValue());
        }

        int convert(int value);
    }
}