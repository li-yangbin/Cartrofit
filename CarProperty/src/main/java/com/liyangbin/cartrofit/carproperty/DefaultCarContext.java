package com.liyangbin.cartrofit.carproperty;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;

import com.liyangbin.cartrofit.flow.Flow;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class DefaultCarContext extends CarPropertyContext implements CarPropertyAccess {

    private static final Timer sTimeoutTracker = new Timer("property_timeout_tracker");

    private static final long DEBOUNCE_TIME_MS = TimeUnit.SECONDS.toMillis(1);
//    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(1);

    private long debounceTimeMillis = DEBOUNCE_TIME_MS;
    private long timeoutMillis = 0;

    public static void registerAsDefault(Context context) {
        ConnectHelper.ensureConnect(context);
        CarPropertyContext.registerScopeProvider(Car.HVAC_SERVICE, HvacContext::new);
        CarPropertyContext.registerScopeProvider(Car.VENDOR_EXTENSION_SERVICE, VendorExtensionContext::new);
        CarPropertyContext.registerScopeProvider(Car.PROPERTY_SERVICE, PropertyContext::new);
        CarPropertyContext.registerScopeProvider(Car.CABIN_SERVICE, CabinContext::new);
    }

    public static boolean isConnected() {
        return ConnectHelper.isConnected();
    }

    static String prop2Str(int property, int area) {
        return "prop:" + property + " hex:0x" + Integer.toHexString(property)
                + " & area:" + area + " hex:0x" + Integer.toHexString(area);
    }

    @SuppressWarnings("unchecked")
    private static <T> T extract(String serviceName) {
        try {
            return (T) ConnectHelper.get(serviceName);
        } catch (CarNotConnectedException connectIssue) {
            throw new RuntimeException(connectIssue);
        }
    }

    private List<CarPropertyConfig> carPropertyConfigs;
    private boolean registered;
    final CopyOnWriteArrayList<FlowSourceImpl> flowSourceList = new CopyOnWriteArrayList<>();

    public synchronized void setDebounceMillis(long debounceTimeMillis) {
        this.debounceTimeMillis = debounceTimeMillis;
    }

    public synchronized void setTimeOutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    private class FlowSourceImpl implements Flow.FlowSource<CarPropertyValue<?>> {
        final int propertyId;
        final int area;
        long blockUntilMillis;
        TimerTask timeoutTask;
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

        void sendPropertyResponse(CarPropertyValue<?> carPropertyValue) {
            int propertyId = carPropertyValue.getPropertyId();
            if (this.propertyId != propertyId) {
                return;
            }
            int area = carPropertyValue.getAreaId();
            if (this.area == 0 || area == 0 || (this.area & area) != 0) {

                if (timeoutTask != null) {
                    synchronized (this) {
                        if (timeoutTask != null) {
                            timeoutTask.cancel();
                            timeoutTask = null;
                        }
                    }
                }

                if (blockUntilMillis > 0 && System.currentTimeMillis() < blockUntilMillis) {
                    return;
                }
                blockUntilMillis = 0;

                for (Flow.Injector<CarPropertyValue<?>> injector : flowInjectors) {
                    injector.send(carPropertyValue);
                }
            }
        }

        synchronized void onPropertySetCalled() {
            if (debounceTimeMillis > 0) {
                blockUntilMillis = System.currentTimeMillis() + debounceTimeMillis;
            }
            if (timeoutMillis > 0) {
                if (timeoutTask != null) {
                    timeoutTask.cancel();
                }
                sTimeoutTracker.schedule(timeoutTask = new TimerTask() {
                    @Override
                    public void run() {
                        onTimeout();
                    }
                }, timeoutMillis);
            }
        }

        @Override
        public void startWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
            flowInjectors.add(injector);
        }

        @Override
        public void finishWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
            flowInjectors.remove(injector);
        }

        synchronized void onTimeout() {
            timeoutTask = null;
            for (Flow.Injector<CarPropertyValue<?>> injector : flowInjectors) {
                injector.error(new TimeoutException("property timeout " + prop2Str(propertyId, area)));
            }
        }
    }

    @Override
    public boolean onPropertySetIntercept(int propId, int area, Object value) {
        for (FlowSourceImpl flowSource : flowSourceList) {
            if (flowSource.match(propId, area)) {
                flowSource.onPropertySetCalled();
            }
        }
        return false;
    }

    @Override
    public CarPropertyConfig<?> getConfig(int propertyId, int area) throws CarNotConnectedException {
        if (carPropertyConfigs == null) {
            carPropertyConfigs = onLoadConfig();
        }
        for (int i = 0; i < carPropertyConfigs.size(); i++) {
            CarPropertyConfig<?> carPropertyConfig = carPropertyConfigs.get(i);
            if (carPropertyConfig.getPropertyId() == propertyId) {
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
            flowSource.sendPropertyResponse(carPropertyValue);
        }
    }

    void error(int propertyId, int area) {
        for (FlowSourceImpl flowSource : flowSourceList) {
            if (flowSource.match(propertyId, area)) {
                for (Flow.Injector<CarPropertyValue<?>> injector : flowSource.flowInjectors) {
                    injector.error(new CarPropertyException(propertyId, area));
                }
                break;
            }
        }
    }

    private static class HvacContext extends DefaultCarContext implements CarHvacManager.CarHvacEventCallback {
        CarHvacManager carHvacManager;

        HvacContext() {
            ConnectHelper.addOnConnectAction(car -> carHvacManager = extract(Car.HVAC_SERVICE));
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

    private static class VendorExtensionContext extends DefaultCarContext
            implements CarVendorExtensionManager.CarVendorExtensionCallback {
        CarVendorExtensionManager carVendorExtensionManager;
        VendorExtensionContext() {
            ConnectHelper.addOnConnectAction(car ->
                    carVendorExtensionManager = extract(Car.VENDOR_EXTENSION_SERVICE));
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

        PropertyContext() {
            ConnectHelper.addOnConnectAction(car -> carPropertyManager = extract(Car.PROPERTY_SERVICE));
        }

        class PropRegisteredSource extends FlowSourceImpl implements CarPropertyManager.CarPropertyEventListener {
            boolean expired;

            PropRegisteredSource(int propertyId, int area) {
                super(propertyId, area);
            }

            void register() throws CarNotConnectedException {
                carPropertyManager.registerListener(this, propertyId, 0f);
            }

            @Override
            public void finishWithInjector(Flow.Injector<CarPropertyValue<?>> injector) {
                super.finishWithInjector(injector);

                if (flowInjectors.size() == 0 && !expired) {
                    synchronized (this) {
                        if (!expired) {
                            carPropertyManager.unregisterListener(this);
                            flowSourceList.remove(this);
                            expired = true;
                        }
                    }
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
                CarPropertyException exception = new CarPropertyException(propertyId, area);
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

        @Override
        public TypedCarPropertyAccess<Integer> getIntCarPropertyAccess() {
            return new TypedCarPropertyAccess<Integer>() {
                @Override
                public Integer get(int propertyId, int area) throws CarNotConnectedException {
                    return carPropertyManager.getIntProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                    carPropertyManager.setIntProperty(propertyId, area, value);
                }
            };
        }

        @Override
        public TypedCarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
            return new TypedCarPropertyAccess<Boolean>() {
                @Override
                public Boolean get(int propertyId, int area) throws CarNotConnectedException {
                    return carPropertyManager.getBooleanProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Boolean value) throws CarNotConnectedException {
                    carPropertyManager.setBooleanProperty(propertyId, area, value);
                }
            };
        }

        @Override
        public TypedCarPropertyAccess<Float> getFloatCarPropertyAccess() {
            return new TypedCarPropertyAccess<Float>() {
                @Override
                public Float get(int propertyId, int area) throws CarNotConnectedException {
                    return carPropertyManager.getFloatProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Float value) throws CarNotConnectedException {
                    carPropertyManager.setFloatProperty(propertyId, area, value);
                }
            };
        }
    }

    private static class CabinContext extends DefaultCarContext implements CarCabinManager.CarCabinEventCallback {
        CarCabinManager carCabinManager;
        CabinContext() {
            ConnectHelper.addOnConnectAction(car -> carCabinManager = extract(Car.CABIN_SERVICE));
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

        @Override
        public TypedCarPropertyAccess<Integer> getIntCarPropertyAccess() {
            return new TypedCarPropertyAccess<Integer>() {
                @Override
                public Integer get(int propertyId, int area) throws CarNotConnectedException {
                    return carCabinManager.getIntProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Integer value) throws CarNotConnectedException {
                    carCabinManager.setIntProperty(propertyId, area, value);
                }
            };
        }

        @Override
        public TypedCarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
            return new TypedCarPropertyAccess<Boolean>() {
                @Override
                public Boolean get(int propertyId, int area) throws CarNotConnectedException {
                    return carCabinManager.getBooleanProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Boolean value) throws CarNotConnectedException {
                    carCabinManager.setBooleanProperty(propertyId, area, value);
                }
            };
        }

        @Override
        public TypedCarPropertyAccess<Float> getFloatCarPropertyAccess() {
            return new TypedCarPropertyAccess<Float>() {
                @Override
                public Float get(int propertyId, int area) throws CarNotConnectedException {
                    return carCabinManager.getFloatProperty(propertyId, area);
                }

                @Override
                public void set(int propertyId, int area, Float value) throws CarNotConnectedException {
                    carCabinManager.setFloatProperty(propertyId, area, value);
                }
            };
        }
    }
}
