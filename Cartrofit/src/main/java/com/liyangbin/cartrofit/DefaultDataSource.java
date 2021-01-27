package com.liyangbin.cartrofit;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyManager;

class DefaultDataSource {

    static void register() {
        CarPropertyContext.addCarPropertyHandler(Car.HVAC_SERVICE, HvacContext::new);
        CarPropertyContext.addCarPropertyHandler(Car.VENDOR_EXTENSION_SERVICE, VendorExtensionContext::new);
        CarPropertyContext.addCarPropertyHandler(Car.PROJECTION_SERVICE, PropertyContext::new);
        CarPropertyContext.addCarPropertyHandler(Car.CABIN_SERVICE, CabinContext::new);
    }

    @SuppressWarnings("unchecked")
    private static <T> T extract(Car car, String serviceName) {
        try {
            return (T) car.getCarManager(serviceName);
        } catch (CarNotConnectedException connectIssue) {
            throw new RuntimeException(connectIssue);
        }
    }

    private static class HvacContext extends Context {
        CarHvacManager carHvacManager;
        HvacContext(Car car) {
            super(CarPropertyContext.getInstance());
            carHvacManager = extract(car, Car.HVAC_SERVICE);
        }
    }

    private static class VendorExtensionContext extends Context {
        CarVendorExtensionManager carVendorExtensionManager;
        VendorExtensionContext(Car car) {
            super(CarPropertyContext.getInstance());
            carVendorExtensionManager = extract(car, Car.VENDOR_EXTENSION_SERVICE);
        }
    }

    private static class PropertyContext extends Context {
        CarPropertyManager carPropertyManager;
        PropertyContext(Car car) {
            super(CarPropertyContext.getInstance());
            carPropertyManager = extract(car, Car.PROPERTY_SERVICE);
        }
    }

    private static class CabinContext extends Context {
        CarCabinManager carCabinManager;
        CabinContext(Car car) {
            super(CarPropertyContext.getInstance());
            carCabinManager = extract(car, Car.CABIN_SERVICE);
        }
    }

    /*private Object mTargetManager;
    private CarPropertyManager mPropertyManager;
    private final SparseArray<CarPropertyConfig> mConfigMap = new SparseArray<>();
    private final Set<Integer> mRegistered = new HashSet<>();
    private final String mKey;
    private final SparseArray<AreaPool> mPublishedFlowList = new SparseArray<>();

    static CommonCarDataSource create(String key) {
        if (Car.HVAC_SERVICE.equals(key)) {
            return new CommonCarDataSource(key);
        } else if (Car.VENDOR_EXTENSION_SERVICE.equals(key)) {
            return new CommonCarDataSource(key);
        } else if (Car.PROPERTY_SERVICE.equals(key)) {
            return new CommonCarDataSource(key);
        } else if (Car.CABIN_SERVICE.equals(key)) {
            return new CommonCarDataSource(key);
        } else {
            return null;
        }
    }

    private CommonCarDataSource(String key) {
        mKey = key;
        ConnectHelper.addOnConnectAction(() -> {
            try {
                mTargetManager = ConnectHelper.get(key);
                mPropertyManager = ConnectHelper.get(Car.PROPERTY_SERVICE);
            } catch (CarNotConnectedException e) {
                throw new RuntimeException(e);
            }
            List<CarPropertyConfig> configList;
            try {
                if (Car.HVAC_SERVICE.equals(key)) {
                    CarHvacManager manager = (CarHvacManager) mTargetManager;
                    configList = manager.getPropertyList();
                } else if (Car.VENDOR_EXTENSION_SERVICE.equals(key)) {
                    CarVendorExtensionManager manager = (CarVendorExtensionManager) mTargetManager;
                    configList = manager.getProperties();
                } else if (Car.PROPERTY_SERVICE.equals(key)) {
                    CarPropertyManager manager = (CarPropertyManager) mTargetManager;
                    configList = manager.getPropertyList();
                } else if (Car.CABIN_SERVICE.equals(key)) {
                    CarCabinManager manager = (CarCabinManager) mTargetManager;
                    configList = manager.getPropertyList();
                } else {
                    throw new IllegalStateException("key:" + key + " is not supported for CommonCarDataSource");
                }
            } catch (CarNotConnectedException impossible) {
                throw new RuntimeException(impossible);
            }
            if (configList != null) {
                for (int i = 0; i < configList.size(); i++) {
                    CarPropertyConfig config = configList.get(i);
                    mConfigMap.put(config.getPropertyId(), config);
                }
            }
        });
    }

    String getKey() {
        return mKey;
    }

    @Override
    public Object get(int key, int area, CarType type) {
        try {
            return ConnectHelper.isConnected() ? getOrThrow(key, area, type)
                    : getValueIfNotConnect(extractValueType(key));
        } catch (CarNotConnectedException impossible) {
            throw new RuntimeException(impossible);
        }
    }

    private static Object getValueIfNotConnect(Class<?> clazz) {
        if (clazz == boolean.class || clazz == Boolean.class) {
            return false;
        } else if (clazz.isPrimitive() || Number.class.isAssignableFrom(clazz)) {
            return 0;
        } else {
            return null;
        }
    }

    private Object getOrThrow(int key, int area, CarType type) throws CarNotConnectedException {
        switch (type) {
            case VALUE:
                return mPropertyManager.getProperty(extractValueType(key), key, area);
            case AVAILABILITY:
                return mPropertyManager.isPropertyAvailable(key, area);
            case CONFIG:
                return mConfigMap.get(key);
            case ALL:
            default:
                throw new IllegalArgumentException("Can not call get() with " + type + " type");
        }
    }

    @Override
    public <TYPE> void set(int key, int area, TYPE value) {
        if (ConnectHelper.isConnected() && mConfigMap.indexOfKey(key) > -1) {
            try {
                mPropertyManager.setProperty(null, key, area, value);
            } catch (CarNotConnectedException impossible) {
                throw new RuntimeException(impossible);
            }
        }
    }

    @Override
    public void onChangeEvent(CarPropertyValue value) {
        synchronized (mPublishedFlowList) {
            AreaPool areaPool = mPublishedFlowList.get(value.getPropertyId());
            if (areaPool != null) {
                areaPool.notifyAreaChange(value);
            }
        }
    }

    @Override
    public void onErrorEvent(int code1, int code2) {
    }

    private static final Flow<CarPropertyValue<?>> sDummyFlow
            = Flow.fromSource(new Flow.FlowSource<CarPropertyValue<?>>() {
        @Override
        public void startWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
        }

        @Override
        public void finishWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
        }

        @Override
        public boolean isHot() {
            return true;
        }
    });

    @Override
    public Flow<CarPropertyValue<?>> track(int key, int area) {
        if (mConfigMap.indexOfKey(key) <= -1) {
            return sDummyFlow;
        }
        if (mRegistered.add(key)) {
            if (ConnectHelper.isConnected()) {
                try {
                    mPropertyManager.registerListener(this, key, 0f);
                } catch (CarNotConnectedException e) {
                    e.printStackTrace();
                }
            }
        }
        AreaPool pool = mPublishedFlowList.get(key);
        if (pool == null) {
            pool = new AreaPool(key);
            mPublishedFlowList.put(key, pool);
        }
        return pool.obtainChildArea(area);
    }

    @Override
    public Object onExtractScope(Class<?> scopeClass) {
        return null;
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
        CarPropertyConfig config = mConfigMap.get(key);
        return config != null ? config.getPropertyType() : null;
    }*/
}
