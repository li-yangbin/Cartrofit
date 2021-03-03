package com.liyangbin.cartrofit.carproperty;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.content.Context;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.carproperty.context.CabinContext;
import com.liyangbin.cartrofit.carproperty.context.HvacContext;
import com.liyangbin.cartrofit.carproperty.context.PropertyContext;
import com.liyangbin.cartrofit.carproperty.context.VendorExtensionContext;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowPublisher;
import com.liyangbin.cartrofit.flow.LifeAwareHotFlowSource;
import com.liyangbin.cartrofit.solution.SolutionProvider;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public abstract class CarPropertyContext<CAR> extends CartrofitContext implements CarAvailabilityListener {

    private static final SolutionProvider CAR_PROPERTY_SOLUTION = new SolutionProvider();
    private static final Cartrofit.ContextFactory<CarPropertyScope> SCOPE_FACTORY =
            Cartrofit.createSingletonFactory(CarPropertyScope.class, CarPropertyScope::value);

    static {
        CAR_PROPERTY_SOLUTION.create(Get.class, PropertyGet.class)
                .provide((get, key) -> new PropertyGet(key.getScope(), get));

        CAR_PROPERTY_SOLUTION.create(Set.class, PropertySet.class)
                .provide((set, key) -> new PropertySet(key.getScope(), set));

        CAR_PROPERTY_SOLUTION.createWithFixedType(Track.class, PropertyTrack.class)
                .provideAndBuildParameter((track, key) -> new PropertyTrack(key.getScope(), track))
                .takeAny()
                .output((a, old, para) -> {
                    if (para.getParameter().getType().equals(CarPropertyValue.class)) {
                        para.set(old);
                    } else {
                        para.set(old.getValue());
                    }
                    return old;
                }).build()
                .takeWithAnnotation(boolean.class, Availability.class)
                .output((a, old, para) -> {
                    para.set(old.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
                    return old;
                }).build()
                .takeWithAnnotation(int.class, Availability.class)
                .output((a, old, para) -> {
                    para.set(old.getStatus());
                    return old;
                }).buildAndCommit();
    }

    public static void registerScopeProvider(String scope, Supplier<CarPropertyContext> provider) {
        SCOPE_FACTORY.register(scope, provider);
    }

    public static String prop2Str(int property, int area) {
        return "prop:" + property + " hex:0x" + Integer.toHexString(property)
                + " & area:" + area + " hex:0x" + Integer.toHexString(area);
    }
    private static final Timer sTimeoutTracker = new Timer("timeout_tracker");

    public static final long DEBOUNCE_TIME_MS = 1000;
    public static final long TIMEOUT_MS = 0;

    public static void registerAsDefault(Context context) {
        DefaultCarServiceAccess.ensureConnect(context);

        CarPropertyContext.registerScopeProvider(Car.HVAC_SERVICE,
                () -> new HvacContext(context));
        CarPropertyContext.registerScopeProvider(Car.VENDOR_EXTENSION_SERVICE,
                () -> new VendorExtensionContext(context));
        CarPropertyContext.registerScopeProvider(Car.PROPERTY_SERVICE,
                () -> new PropertyContext(context));
        CarPropertyContext.registerScopeProvider(Car.CABIN_SERVICE,
                () -> new CabinContext(context));
    }

    private long debounceTimeMillis = DEBOUNCE_TIME_MS;
    private long timeoutMillis = TIMEOUT_MS;
    private List<CarPropertyConfig> carPropertyConfigs;

    private CarServiceAccess<CAR> serviceAccess;
    private CAR manager;
    protected boolean registerOnceFlowSource = true;
    private boolean registeredForAll;
    protected final CopyOnWriteArrayList<PropertyFlowSource> flowSourceList = new CopyOnWriteArrayList<>();

    public CarPropertyContext(CarServiceAccess<CAR> serviceAccess) {
        this.serviceAccess = Objects.requireNonNull(serviceAccess);
        serviceAccess.tryConnect();
        serviceAccess.addOnCarAvailabilityListener(this);
    }

    public void setDebounceMillis(long debounceTimeMillis) {
        this.debounceTimeMillis = debounceTimeMillis;
    }

    public void setTimeOutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public SolutionProvider onProvideCallSolution() {
        return CAR_PROPERTY_SOLUTION;
    }

    public CAR getManagerLazily() throws CarNotConnectedException {
        if (manager != null) {
            return manager;
        }
        synchronized (this) {
            if (manager == null) {
                manager = serviceAccess.get();
            }
            return manager;
        }
    }

    public final boolean isAvailable() {
        return serviceAccess.isAvailable();
    }

    @Override
    public Object onInterceptCallInvocation(Call call, Object[] parameter) throws Throwable {
        if (call instanceof PropertySet) {
            return onInterceptPropertySet((PropertySet) call) ? null : Cartrofit.SKIP;
        } else if (call instanceof PropertyGet) {
            PropertyGet propertyGet = (PropertyGet) call;
            return onInterceptPropertyGet(propertyGet, propertyGet.getType);
        } else if (call instanceof PropertyTrack) {
            Flow<CarPropertyValue<?>> interceptedFlow = onInterceptPropertyTrack((PropertyTrack) call);
            return interceptedFlow != null ? interceptedFlow : Cartrofit.SKIP;
        }
        return Cartrofit.SKIP;
    }

    @Override
    public void onCarAvailable() {
        synchronized (CarPropertyContext.this) {
            try {
                if (registerOnceFlowSource) {
                    onRegister(null);
                    registeredForAll = true;
                } else {
                    for (PropertyFlowSource flowSource : flowSourceList) {
                        flowSource.registerOnAttempt();
                    }
                }
            } catch (CarNotConnectedException connectedException) {
                throw new RuntimeException("impossible", connectedException);
            }
        }
    }

    @Override
    public void onCarUnavailable() {
        synchronized (CarPropertyContext.this) {
            manager = null;
        }
    }

    public boolean onInterceptPropertySet(PropertySet propertySet)
            throws Throwable {
        for (PropertyFlowSource flowSource : flowSourceList) {
            if (flowSource.match(propertySet.getPropertyId(), propertySet.getAreaId())) {
                flowSource.onPropertySetCalled();
            }
        }
        return false;
    }

    public Object onInterceptPropertyGet(PropertyGet propertyGet, CarType carType)
            throws Throwable {
        return Cartrofit.SKIP;
    }

    public Flow<CarPropertyValue<?>> onInterceptPropertyTrack(PropertyTrack propertyTrack)
            throws Throwable {
        return null;
    }

    public class PropertyFlowSource extends LifeAwareHotFlowSource<CarPropertyValue<?>> {
        public final int propertyId;
        public final int area;
        public boolean registered;
        private boolean registerAttempted;
        long blockUntilMillis;
        TimerTask timeoutTask;

        public PropertyFlowSource(int propertyId, int area) {
            this.propertyId = propertyId;
            this.area = area;
        }

        public boolean match(int propertyId, int area) {
            return propertyMatch(this.propertyId, this.area, propertyId, area);
        }

        public void publishPropertyChange(CarPropertyValue<?> carPropertyValue) {
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

            publish(carPropertyValue);
        }

        synchronized void onPropertySetCalled() {
            if (debounceTimeMillis > 0) {
                blockUntilMillis = System.currentTimeMillis() + debounceTimeMillis;
            }
            if (timeoutMillis > 0) {
                if (timeoutTask != null) {
                    timeoutTask.cancel();
                }
                sTimeoutTracker.schedule(timeoutTask = new TimeoutTask(), timeoutMillis);
            }
        }

        void registerOnAttempt() throws CarNotConnectedException {
            if (!registerAttempted) {
                return;
            }
            if (registerOnceFlowSource) {
                if (!registeredForAll) {
                    synchronized (CarPropertyContext.this) {
                        if (!registeredForAll) {
                            onRegister(this);
                            registeredForAll = true;
                        }
                    }
                }
            } else if (!registered) {
                synchronized (CarPropertyContext.this) {
                    if (!registered) {
                        onRegister(this);
                        registered = true;
                    }
                }
            }
        }

        @Override
        public void onActive() {
            try {
                registerAttempted = true;
                registerOnAttempt();
            } catch (CarNotConnectedException error) {
                publishError(error);
            }
        }

        private class TimeoutTask extends TimerTask {

            @Override
            public void run() {
                synchronized(PropertyFlowSource.this) {
                    if (timeoutTask == this) {
                        timeoutTask = null;
                        publishError(new TimeoutException("property timeout "
                                + prop2Str(propertyId, area)));
                    }
                }
            }
        }
    }

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
        throw new RuntimeException("Failed to find config for property:"
                + prop2Str(propertyId, area));
    }

    public void send(CarPropertyValue<?> carPropertyValue) {
        for (PropertyFlowSource flowSource : flowSourceList) {
            if (flowSource.match(carPropertyValue.getPropertyId(), carPropertyValue.getAreaId())) {
                flowSource.publishPropertyChange(carPropertyValue);
                break;
            }
        }
    }

    public void error(int propertyId, int area) {
        for (PropertyFlowSource flowSource : flowSourceList) {
            if (flowSource.match(propertyId, area)) {
                flowSource.publishError(new CarPropertyException(propertyId, area));
                break;
            }
        }
    }

    public Flow<CarPropertyValue<?>> track(int propertyId, int area) {
        PropertyFlowSource source = null;
        for (PropertyFlowSource flowSource : flowSourceList) {
            if (flowSource.match(propertyId, area)) {
                source = flowSource;
                break;
            }
        }
        if (source == null) {
            source = onCreatePropertyFlowSource(propertyId, area);
            flowSourceList.add(source);
        }
        return Flow.fromSource(source);
    }

    // ================ CarXXXXManager Abstract Interface ================

    public boolean propertyMatch(int this_propertyId, int this_area,
                                 int that_propertyId, int that_area) {
        if (this_propertyId != that_propertyId) {
            return false;
        }
        if (this_area == 0 || that_area == 0) {
            return true;
        }
        return (this_area & that_area) != 0;
    }

    public abstract List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException;

    public abstract void onRegister(PropertyFlowSource flowSource) throws CarNotConnectedException;

    public abstract boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException;

    public PropertyFlowSource onCreatePropertyFlowSource(int propertyId, int area) {
        return new PropertyFlowSource(propertyId, area);
    }

    public CarPropertyAccess<?> getCarPropertyAccess(Class<?> type) {
        if (classEquals(type, int.class)) {
            return getIntCarPropertyAccess();
        } else if (classEquals(type, byte[].class)) {
            return getByteArrayCarPropertyAccess();
        } else if (classEquals(type, boolean.class)) {
            return getBooleanCarPropertyAccess();
        } else if (classEquals(type, float.class)) {
            return getFloatCarPropertyAccess();
        } else if (classEquals(type, byte.class)) {
            return getByteCarPropertyAccess();
        } else if (classEquals(type, String.class)) {
            return getStringCarPropertyAccess();
        }
        throw new RuntimeException("Sub-class should implement this method type:" + type);
    }

    public CarPropertyAccess<Integer> getIntCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public CarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public CarPropertyAccess<Float> getFloatCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public CarPropertyAccess<Byte> getByteCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public CarPropertyAccess<String> getStringCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public CarPropertyAccess<byte[]> getByteArrayCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    // =================== END ===================

    public enum CarType {
        VALUE, // CarPropertyValue.getValue()
        AVAILABILITY, // CarPropertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE
        CONFIG, // CarPropertyConfig
    }

    private static class BuildInValue {
        int intValue;

        boolean booleanValue;

        long longValue;

        byte byteValue;
        byte[] byteArray;

        float floatValue;

        String stringValue;

        static BuildInValue build(CarValue value) {
            if (CarValue.EMPTY_VALUE.equals(value.string())) {
                return null;
            }
            BuildInValue result = new BuildInValue();

            result.intValue = value.Int();
            result.booleanValue = value.Boolean();

            result.byteValue = value.Byte();
            result.byteArray = value.ByteArray();

            result.floatValue = value.Float();
            result.longValue = value.Long();
            result.stringValue = value.string();

            return result;
        }

        Object extractValue(Class<?> clazz) {
            if (String.class == clazz) {
                return stringValue;
            } else if (int.class == clazz || Integer.class == clazz) {
                return intValue;
            } else if (boolean.class == clazz || Boolean.class == clazz) {
                return booleanValue;
            } else if (byte.class == clazz || Byte.class == clazz) {
                return byteValue;
            } else if (byte[].class == clazz) {
                return byteArray;
            } else if (float.class == clazz || Float.class == clazz) {
                return floatValue;
            } else if (long.class == clazz || Long.class == clazz) {
                return longValue;
            } else {
                throw new CartrofitGrammarException("invalid type:" + clazz);
            }
        }
    }

    private static int resolveArea(int handleArea, int scopeArea) {
        return handleArea == CarPropertyScope.DEFAULT_AREA_ID ? scopeArea : handleArea;
    }

    public static abstract class PropertyAccessCall<IN, OUT> extends FixedTypeCall<IN, OUT> {
        CarPropertyAccess<Object> carPropertyTypeAccess;

        CarPropertyConfig<?> propertyConfig;
        int propertyId;
        int areaId;

        public PropertyAccessCall(int propertyId, int areaId) {
            this.propertyId = propertyId;
            this.areaId = areaId;
        }

        public CarPropertyConfig<?> getPropertyConfig() throws CarNotConnectedException {
            if (propertyConfig != null) {
                return propertyConfig;
            }
            synchronized (this) {
                if (propertyConfig == null) {
                    propertyConfig = getContext().getConfig(propertyId, areaId);
                }
                return propertyConfig;
            }
        }

        public CarPropertyAccess<Object> getPropertyAccess() throws CarNotConnectedException {
            if (carPropertyTypeAccess != null) {
                return carPropertyTypeAccess;
            }
            synchronized (this) {
                return carPropertyTypeAccess = (CarPropertyAccess<Object>) getContext()
                        .getCarPropertyAccess(getPropertyConfig().getPropertyType());
            }
        }

        @Override
        public CarPropertyContext getContext() {
            return (CarPropertyContext) super.getContext();
        }

        public int getPropertyId() {
            return propertyId;
        }

        public int getAreaId() {
            return areaId;
        }
    }

    public static class PropertyGet extends PropertyAccessCall<Void, Void> {
        CarType getType = CarType.VALUE;

        public PropertyGet(CarPropertyScope scope, Get get) {
            super(get.propId(), resolveArea(get.area(), scope.area()));
        }

        @Override
        public void onInit() {
            super.onInit();
            Class<?> returnType = getKey().getReturnType();
            if (returnType.equals(CarPropertyConfig.class)) {
                getType = CarType.CONFIG;
            } else if (CartrofitContext.classEquals(returnType, boolean.class)
                    && getKey().isAnnotationPresent(Availability.class)) {
                getType = CarType.AVAILABILITY;
            }
        }

        @Override
        public Object invoke(Object[] parameter) throws CarNotConnectedException {
            if (getType == CarType.CONFIG) {
                return getPropertyConfig();
            } else if (getType == CarType.AVAILABILITY) {
                return getContext().isPropertyAvailable(propertyId, areaId);
            } else {
                return getPropertyAccess().get(propertyId, areaId);
            }
        }
    }

    public static class PropertySet extends PropertyAccessCall<Void, Void> {
        Object buildInSetValue;
        BuildInValue buildInValueUnresolved;

        public PropertySet(CarPropertyScope scope, Set set) {
            super(set.propId(), resolveArea(set.area(), scope.area()));
            buildInValueUnresolved = BuildInValue.build(set.value());
        }

        public Object resolveSetValue(Object[] parameter) throws CarNotConnectedException {
            if (buildInValueUnresolved != null) {
                synchronized (this) {
                    if (buildInValueUnresolved != null) {
                        buildInSetValue = buildInValueUnresolved
                                .extractValue(getPropertyConfig().getPropertyType());
                        buildInValueUnresolved = null;
                    }
                }
            }
            return buildInSetValue != null ? buildInSetValue : parameter[0];
        }

        @Override
        public Object invoke(Object[] parameter) throws CarNotConnectedException {
            getPropertyAccess().set(propertyId, areaId, resolveSetValue(parameter));
            return null;
        }
    }

    public static class PropertyTrack extends PropertyAccessCall<Void, CarPropertyValue<?>> {

        boolean isSticky;
        boolean restoreDataWhenTimeout;
        FlowPublisher<CarPropertyValue<?>> carValuePublisher;

        public PropertyTrack(CarPropertyScope scope, Track track) {
            super(track.propId(), resolveArea(track.area(), scope.area()));
            this.isSticky = track.sticky();
            this.restoreDataWhenTimeout = track.restoreIfTimeout();
        }

        public CarPropertyValue<Object> onLoadDefaultData() throws CarNotConnectedException {
            return new CarPropertyValue<>(propertyId,
                    areaId, getPropertyAccess().get(propertyId, areaId));
        }

        public final FlowPublisher<CarPropertyValue<?>> getPropertyPublisher() {
            if (carValuePublisher != null) {
                return carValuePublisher;
            }
            synchronized (this) {
                if (carValuePublisher == null) {
                    carValuePublisher = getContext().track(propertyId, areaId).publish();
                    carValuePublisher.setDispatchStickyDataEnable(isSticky, this::onLoadDefaultData);
                }
                return carValuePublisher;
            }
        }

        @Override
        public Flow<CarPropertyValue<?>> onTrackInvoke(Void none) {
            return getPropertyPublisher().share().catchException(TimeoutException.class, e -> {
                e.printStackTrace();
                if (restoreDataWhenTimeout && carValuePublisher != null) {
                    carValuePublisher.injectData(carValuePublisher.getData());
                }
            });
        }
    }
}
