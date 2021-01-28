package com.liyangbin.cartrofit;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyManager;

import com.liyangbin.cartrofit.flow.Flow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class DefaultCarContext extends CarPropertyContext implements CarPropertyAccess {

    static void register() {
        CarPropertyContext.addCarPropertyHandler(Car.HVAC_SERVICE, HvacContext::new);
        CarPropertyContext.addCarPropertyHandler(Car.VENDOR_EXTENSION_SERVICE, VendorExtensionContext::new);
        CarPropertyContext.addCarPropertyHandler(Car.PROJECTION_SERVICE, PropertyContext::new);
        CarPropertyContext.addCarPropertyHandler(Car.CABIN_SERVICE, CabinContext::new);
    }

    static String prop2Str(int property, int area) {
        return "property:" + property + " hex:0x" + Integer.toHexString(property)
                + " -- area:" + area + " hex:0x" + Integer.toHexString(area);
    }

    @SuppressWarnings("unchecked")
    private static <T> T extract(Car car, String serviceName) {
        try {
            return (T) car.getCarManager(serviceName);
        } catch (CarNotConnectedException connectIssue) {
            throw new RuntimeException(connectIssue);
        }
    }

    private List<CarPropertyConfig> carPropertyConfigs;
    private boolean registered;
    final CopyOnWriteArrayList<FlowSourceImpl> flowSourceList = new CopyOnWriteArrayList<>();

    private static class FlowSourceImpl implements Flow.FlowSource<CarPropertyValue<?>> {
        final int propertyId;
        final int area;
        final CopyOnWriteArrayList<Flow.Injector<CarPropertyValue<?>>> flowInjectors = new CopyOnWriteArrayList<>();

        FlowSourceImpl(int propertyId, int area) {
            this.propertyId = propertyId;
            this.area = area;
        }

        boolean match(int propertyId, int area) {
            if (this.propertyId != propertyId) {
                return false;
            }
            if (this.area == 0 || area == 0) {
                return true;
            }
            return (this.area & area) != 0;
        }

        @Override
        public void startWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
            flowInjectors.add(injector);
        }

        @Override
        public void finishWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
            flowInjectors.remove(injector);
        }
    }

    @Override
    public CarPropertyConfig<?> getConfig(int propertyId, int area) throws CarNotConnectedException {
        if (carPropertyConfigs == null) {
            carPropertyConfigs = onLoadConfig();
        }
        for (int i = 0; i < carPropertyConfigs.size(); i++) {
            CarPropertyConfig<?> carPropertyConfig = carPropertyConfigs.get(i);
            if (carPropertyConfig.getPropertyId() == propertyId && carPropertyConfig.hasArea(area)) {
                return carPropertyConfig;
            }
        }
        throw new RuntimeException("Failed to find property for property:"
                + prop2Str(propertyId, area));
    }

    @Override
    public Flow<CarPropertyValue<?>> track(int propertyId, int area) throws CarNotConnectedException {
        if (!registered) {
            onRegister();
            registered = true;
        }
        FlowSourceImpl source = null;
        for (FlowSourceImpl flowSource : flowSourceList) {
            if (flowSource.match(propertyId, area)) {
                source = flowSource;
                break;
            }
        }
        if (source == null) {
            source = new FlowSourceImpl(propertyId, area);
            flowSourceList.add(source);
        }
        return Flow.fromSource(source);
    }

    public abstract List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException;

    public abstract void onRegister() throws CarNotConnectedException;

    @Override
    public abstract boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException;

    @Override
    public CarPropertyAccess getCarPropertyAccess() {
        return this;
    }

    void send(CarPropertyValue<?> carPropertyValue) {
        for (FlowSourceImpl flowSource : flowSourceList) {
            if (flowSource.match(carPropertyValue.getPropertyId(), carPropertyValue.getAreaId())) {
                for (Flow.Injector<CarPropertyValue<?>> injector : flowSource.flowInjectors) {
                    injector.send(carPropertyValue);
                }
                break;
            }
        }
    }

    void error(int propertyId, int area) {
        for (FlowSourceImpl flowSource : flowSourceList) {
            if (flowSource.match(propertyId, area)) {
                for (Flow.Injector<CarPropertyValue<?>> injector : flowSource.flowInjectors) {
                    injector.error(new RuntimeException("property error:" + prop2Str(propertyId, area)));
                }
                break;
            }
        }
    }

    private static class HvacContext extends DefaultCarContext implements CarHvacManager.CarHvacEventCallback {
        CarHvacManager carHvacManager;

        HvacContext(Car car) {
            carHvacManager = extract(car, Car.HVAC_SERVICE);
        }

        @Override
        public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
            return carHvacManager.getPropertyList();
        }

        @Override
        public void onRegister() throws CarNotConnectedException {
            carHvacManager.registerCallback(this);
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
        public boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException {
            return carHvacManager.isPropertyAvailable(propertyId, area);
        }

        @Override
        public TypedCarPropertyAccess<Integer> getIntCarPropertyAccess() {
            return new TypedCarPropertyAccess<Integer>() {
                @Override
                public Integer get(int propertyId, int area) throws CarNotConnectedException {
                    return carHvacManager.getIntProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                    carHvacManager.setIntProperty(propertyId, area, value);
                }
            };
        }

        @Override
        public TypedCarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
            return new TypedCarPropertyAccess<Boolean>() {
                @Override
                public Boolean get(int propertyId, int area) throws CarNotConnectedException {
                    return carHvacManager.getBooleanProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Boolean value) throws CarNotConnectedException {
                    carHvacManager.setBooleanProperty(propertyId, area, value);
                }
            };
        }

        @Override
        public TypedCarPropertyAccess<Float> getFloatCarPropertyAccess() {
            return new TypedCarPropertyAccess<Float>() {
                @Override
                public Float get(int propertyId, int area) throws CarNotConnectedException {
                    return carHvacManager.getFloatProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Float value) throws CarNotConnectedException {
                    carHvacManager.setFloatProperty(propertyId, area, value);
                }
            };
        }
    }

    private static class VendorExtensionContext extends DefaultCarContext implements CarVendorExtensionManager.CarVendorExtensionCallback {
        CarVendorExtensionManager carVendorExtensionManager;
        VendorExtensionContext(Car car) {
            carVendorExtensionManager = extract(car, Car.VENDOR_EXTENSION_SERVICE);
        }

        @Override
        public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
            return carVendorExtensionManager.getProperties();
        }

        @Override
        public void onRegister() throws CarNotConnectedException {
            carVendorExtensionManager.registerCallback(this);
        }

        @Override
        public boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException {
            return carVendorExtensionManager.isPropertyAvailable(propertyId, area);
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
        public TypedCarPropertyAccess<?> getTypedCarPropertyAccess(Class<?> type) {
            return new TypedCarPropertyAccess<Object>() {
                @Override
                public Object get(int propertyId, int area) throws CarNotConnectedException {
                    return carVendorExtensionManager.getProperty(type, propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Object value) throws CarNotConnectedException {
                    carVendorExtensionManager.setProperty((Class<Object>)type, propertyId, area, value);
                }
            };
        }
    }

    private static class PropertyContext extends DefaultCarContext {
        CarPropertyManager carPropertyManager;

        PropertyContext(Car car) {
            carPropertyManager = extract(car, Car.PROPERTY_SERVICE);
        }

        class PropRegisteredSource extends FlowSourceImpl implements CarPropertyManager.CarPropertyEventListener {

            PropRegisteredSource(int propertyId, int area) {
                super(propertyId, area);
            }

            void register() throws CarNotConnectedException {
                carPropertyManager.registerListener(this, propertyId, 0f);
            }

            @Override
            public void finishWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
                super.finishWithInjector(injector);
                if (flowInjectors.size() == 0) {
                    carPropertyManager.unregisterListener(this);
                    flowSourceList.remove(this);
                }
            }

            @Override
            public void onChangeEvent(CarPropertyValue carPropertyValue) {
                for (Flow.Injector<CarPropertyValue<?>> injector : flowInjectors) {
                    injector.send(carPropertyValue);
                }
            }

            @Override
            public void onErrorEvent(int propertyId, int area) {
                RuntimeException exception = new RuntimeException("Property property error:" + prop2Str(propertyId, area));
                for (Flow.Injector<CarPropertyValue<?>> injector : flowInjectors) {
                    injector.error(exception);
                }
            }
        }

        @Override
        public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
            return carPropertyManager.getPropertyList();
        }

        @Override
        public Flow<CarPropertyValue<?>> track(int propertyId, int area) throws CarNotConnectedException {
            PropRegisteredSource source = null;
            for (FlowSourceImpl flowSource : flowSourceList) {
                if (flowSource.match(propertyId, area)) {
                    source = (PropRegisteredSource) flowSource;
                    break;
                }
            }
            if (source == null) {
                source = new PropRegisteredSource(propertyId, area);
                source.register();
                flowSourceList.add(source);
            }
            return Flow.fromSource(source);
        }

        @Override
        public void onRegister() throws CarNotConnectedException {
            // ignore
        }

        @Override
        public boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException {
            return carPropertyManager.isPropertyAvailable(propertyId, area);
        }
    }

    private static class CabinContext extends DefaultCarContext implements CarCabinManager.CarCabinEventCallback {
        CarCabinManager carCabinManager;
        CabinContext(Car car) {
            carCabinManager = extract(car, Car.CABIN_SERVICE);
        }

        @Override
        public List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException {
            return carCabinManager.getPropertyList();
        }

        @Override
        public void onRegister() throws CarNotConnectedException {
            carCabinManager.registerCallback(this);
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
    }
}
