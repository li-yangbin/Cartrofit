package com.liyangbin.carretrofit;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.property.CarPropertyManager;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CarManager2 implements DataSource {

    private CarPropertyManager mManager;
    private CarVendorExtensionManager mExtManager;
    private boolean rootTrack;
    private final HashMap<Integer, AreaPool> publishedFlowList = new HashMap<>();
    private HashMap<Integer, CarPropertyConfig<?>> mConfigMap = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <VALUE> VALUE get(int key, int area, CarType type) throws Exception {
        switch (type) {
            case VALUE:
                return mExtManager.getProperty(extractValueType(key), key, area);
            case AVAILABILITY:
                boolean result = mManager.isPropertyAvailable(key, area);
                return (VALUE) Boolean.valueOf(result);
            case CONFIG:
                if (mConfigMap.containsKey(key)) {
                    return (VALUE) mConfigMap.get(key);
                }
                List<CarPropertyConfig> list = mManager.getPropertyList();
                if (list != null) {
                    list.forEach(carPropertyConfig ->
                            mConfigMap.put(carPropertyConfig.getPropertyId(), carPropertyConfig));
                }
                return (VALUE) mConfigMap.get(key);
            case ALL:
            default:
                throw new IllegalArgumentException("Can not call get() with " + type + " type");
        }
    }

    @Override
    public <VALUE> void set(int key, int area, VALUE value) throws Exception {
        mExtManager.setProperty(extractValueType(key), key, area, value);
    }

    public void trackRootIfNeeded() throws Exception {
        if (!rootTrack) {
            mExtManager.registerCallback(new CarVendorExtensionCallbackImpl());
            rootTrack = true;
        }
    }

    @Override
    public Flow<CarPropertyValue<?>> track(int key, int area) throws Exception {
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
    public <VALUE> Class<VALUE> extractValueType(int key) throws Exception {
        return this.<CarPropertyConfig<VALUE>>get(key, 0, CarType.CONFIG).getPropertyType();
    }
}