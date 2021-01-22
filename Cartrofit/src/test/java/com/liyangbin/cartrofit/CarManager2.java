package com.liyangbin.cartrofit;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.property.CarPropertyManager;

import androidx.annotation.Nullable;

import com.liyangbin.cartrofit.flow.Flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class CarManager2 implements DataSource {

    private CarPropertyManager mManager;
    private CarVendorExtensionManager mExtManager;
    private boolean rootTrack;
    private final HashMap<Integer, AreaPool> publishedFlowList = new HashMap<>();
    private HashMap<Integer, CarPropertyConfig<?>> mConfigMap = new HashMap<>();

    @Override
    public Object get(int key, int area, CarType type) {
        switch (type) {
            case VALUE:
                try {
                    return mExtManager.getProperty(extractValueType(key), key, area);
                } catch (CarNotConnectedException e) {
                    throw new RuntimeException(e);
                }
            case AVAILABILITY:
                boolean result = false;
                try {
                    result = mManager.isPropertyAvailable(key, area);
                } catch (CarNotConnectedException e) {
                    e.printStackTrace();
                }
                return result;
            case CONFIG:
                if (mConfigMap.containsKey(key)) {
                    return mConfigMap.get(key);
                }
                List<CarPropertyConfig> list = null;
                try {
                    list = mManager.getPropertyList();
                } catch (CarNotConnectedException e) {
                    e.printStackTrace();
                }
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
    public <TYPE> void set(int key, int area, TYPE value) {
        try {
            mExtManager.setProperty((Class<? super TYPE>) extractValueType(key), key, area, value);
        } catch (CarNotConnectedException e) {
            e.printStackTrace();
        }
    }

    public void trackRootIfNeeded() {
        if (!rootTrack) {
            try {
                mExtManager.registerCallback(new CarVendorExtensionCallbackImpl());
            } catch (CarNotConnectedException e) {
                e.printStackTrace();
            }
            rootTrack = true;
        }
    }

    @Override
    public Flow<CarPropertyValue<?>> track(int key, int area) {
        trackRootIfNeeded();
        synchronized (publishedFlowList) {
            AreaPool pool = publishedFlowList.get(key);
            if (pool == null) {
                pool = new AreaPool(key);
                publishedFlowList.put(key, pool);
            }
            return pool.obtainChildArea(area);
        }
    }

    public void notifyChange(CarPropertyValue<?> value) {
        synchronized (publishedFlowList) {
            AreaPool areaPool = publishedFlowList.get(value.getPropertyId());
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
                if (flow.areaId == 0 || carValue.getAreaId() == 0 || (flow.areaId & carValue.getAreaId()) != 0) {
                    flow.publishChange(carValue);
                }
            }
        }
    }

    private class CarVendorExtensionCallbackImpl implements CarVendorExtensionManager.CarVendorExtensionCallback {

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            notifyChange(carPropertyValue);
        }

        @Override
        public void onErrorEvent(int i, int i1) {

        }
    }

    private void onObserverActive(int key) {

    }

    private void onObserverInActive(int key) {

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
            if (consumerList.size() == 0) {
                CarManager2.this.onObserverActive(propertyId);
            }
            consumerList.add(Objects.requireNonNull(consumer));
        }

        @Override
        public void removeObserver(Consumer<CarPropertyValue<?>> consumer) {
            if (consumerList.remove(Objects.requireNonNull(consumer)) && consumerList.size() == 0) {
                CarManager2.this.onObserverInActive(propertyId);
            }
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

    @Nullable
    @Override
    public Class<?> extractValueType(int key) {
        return ((CarPropertyConfig<?>)get(key, 0, CarType.CONFIG)).getPropertyType();
    }
}