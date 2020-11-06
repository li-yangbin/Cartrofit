package com.android.car.hvac;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.SparseArray;
import android.util.SparseLongArray;

import com.liyangbin.cartrofit.ApiBuilder;
import com.liyangbin.cartrofit.CarType;
import com.liyangbin.cartrofit.DataSource;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.annotation.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Scope(Car.HVAC_SERVICE)
public class HvacDataSource implements DataSource {

    private static final String TAG = "HvacDataSource";

    private CarPropertyManager mCarPropertyManager;
    private HvacPolicy mPolicy;
    private SparseArray<CarPropertyConfig<?>> mConfigMap = new SparseArray<>();
    private boolean mHvacChangeTracked;
    private final SparseArray<AreaPool> mPublishedFlowList = new SparseArray<>();
    private final SparseLongArray mLastSetMillis = new SparseLongArray();
    private Handler mHandler = new Handler();

    public HvacDataSource(Context context) {
        IBinder binder = new LocalHvacPropertyService().getCarPropertyService();
        mCarPropertyManager = new CarPropertyManager(binder, mHandler, false, TAG);
        List<CarPropertyConfig> list = null;
        try {
            list = mCarPropertyManager.getPropertyList();
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
    public Object get(int key, int area, CarType type) {
        switch (type) {
            case VALUE:
                try {
                    return mCarPropertyManager.getProperty(key, area).getValue();
                } catch (CarNotConnectedException e) {
                    e.printStackTrace();
                }
            case AVAILABILITY:
                try {
                    return mCarPropertyManager.isPropertyAvailable(key, area);
                } catch (CarNotConnectedException e) {
                    e.printStackTrace();
                }
            case CONFIG:
                return mConfigMap.get(key);
            case ALL:
            default:
                throw new IllegalArgumentException("Can not call get() with " + type + " type");
        }
    }

    @Override
    public <TYPE> void set(int key, int area, TYPE value) {
        try {
            mCarPropertyManager.setProperty((Class<? super TYPE>) extractValueType(key), key, area, value);
        } catch (CarNotConnectedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Flow<CarPropertyValue<?>> track(int key, int area) {
        if (!mHvacChangeTracked) {
            try {
                for (int index = 0; index < mConfigMap.size(); index++) {
                    int id = mConfigMap.keyAt(index);
                    mCarPropertyManager.registerListener(new CarPropertyManager.CarPropertyEventListener() {
                        @Override
                        public void onChangeEvent(CarPropertyValue carPropertyValue) {
                            notifyChange(carPropertyValue);
                        }

                        @Override
                        public void onErrorEvent(int i, int i1) {
                        }
                    }, id, 0.0f);
                }
                mHvacChangeTracked = true;
            } catch (CarNotConnectedException e) {
                e.printStackTrace();
            }
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
    public Class<?> extractValueType(int key) {
        return mConfigMap.get(key).getPropertyType();
    }

    /*private void installInterceptor(Command command) {
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
    }*/
}
