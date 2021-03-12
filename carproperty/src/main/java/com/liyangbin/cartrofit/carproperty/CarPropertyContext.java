package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.content.Context;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.Key;
import com.liyangbin.cartrofit.Parameter;
import com.liyangbin.cartrofit.carproperty.context.CabinContext;
import com.liyangbin.cartrofit.carproperty.context.HvacContext;
import com.liyangbin.cartrofit.carproperty.context.PropertyContext;
import com.liyangbin.cartrofit.carproperty.context.VendorExtensionContext;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.solution.SolutionProvider;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public abstract class CarPropertyContext<CAR> extends CarAbstractContext<CAR, CarPropertyContext.PropKey, CarPropertyValue<?>> {

    public static class PropKey {
        int propertyId;
        int area;
        Class<?> preferType;

        PropKey(int propertyId, int area, Class<?> preferType) {
            this.propertyId = propertyId;
            this.area = area;
            this.preferType = preferType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PropKey propKey = (PropKey) o;
            return propertyId == propKey.propertyId &&
                    area == propKey.area && preferType == propKey.preferType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(propertyId, area, preferType);
        }

        @Override
        public String toString() {
            return (preferType != null ? preferType.getSimpleName() : null)
                    + " " + CarPropertyContext.prop2Str(propertyId, area);
        }
    }

    private static final SolutionProvider CAR_PROPERTY_SOLUTION = new SolutionProvider();

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

    public static String prop2Str(int property, int area) {
        return "prop:" + property + " hex:0x" + Integer.toHexString(property)
                + " & area:" + area + " hex:0x" + Integer.toHexString(area);
    }
    private static final Timer sTimeoutTracker = new Timer("timeout_tracker");

    public static final long DEBOUNCE_TIME_MS = 1000;
    public static final long TIMEOUT_MS = 0;

    public static void registerAsDefault(Context context) {
        DefaultCarServiceAccess.ensureConnect(context);

        Cartrofit.register(new HvacContext(context));
        Cartrofit.register(new VendorExtensionContext(context));
        Cartrofit.register(new PropertyContext(context));
        Cartrofit.register(new CabinContext(context));
        Cartrofit.register(new CarSensorContext(context));
    }

    private long debounceTimeMillis = DEBOUNCE_TIME_MS;
    private long timeoutMillis = TIMEOUT_MS;
    private List<CarPropertyConfig> carPropertyConfigs;

    public CarPropertyContext(CarServiceAccess<CAR> serviceAccess) {
        super(serviceAccess);
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

    public boolean onInterceptPropertySet(PropertySet propertySet)
            throws Throwable {
        for (PropertyFlowSource flowSource : this.<PropertyFlowSource>getAllFlowSourceInQuick()) {
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

    public class PropertyFlowSource extends CarFlowSource {
        long blockUntilMillis;
        TimerTask timeoutTask;
        public final int propertyId;
        public final int area;

        public PropertyFlowSource(PropKey propKey) {
            super(propKey);
            this.propertyId = propKey.propertyId;
            this.area = propKey.area;
        }

        public boolean match(int propertyId, int area) {
            return this.propertyId == propertyId && (this.area == area || (this.area & area) != 0);
        }

        public void publishUnchecked(CarPropertyValue<?> carPropertyValue) {
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

        @Override
        public CarPropertyValue<?> loadInitData() throws CarNotConnectedException {
            if (sourceKey.preferType == void.class
                    || sourceKey.preferType == CarPropertyValue.class) {
                return null;
            }
            Object value = getCarPropertyAccess(sourceKey.preferType)
                    .get(sourceKey.propertyId, sourceKey.area);
            return new CarPropertyValue<>(sourceKey.propertyId, sourceKey.area, value);
        }

        private class TimeoutTask extends TimerTask {

            @Override
            public void run() {
                synchronized(PropertyFlowSource.this) {
                    if (timeoutTask == this) {
                        timeoutTask = null;
                        publishError(new TimeoutException("property timeout " + sourceKey));
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
        for (PropertyFlowSource flowSource : this.<PropertyFlowSource>getAllFlowSourceInQuick()) {
            if (flowSource.match(carPropertyValue.getPropertyId(), carPropertyValue.getAreaId())) {
                flowSource.publishUnchecked(carPropertyValue);
            }
        }
    }

    public void error(int propertyId, int area) {
        for (PropertyFlowSource flowSource : this.<PropertyFlowSource>getAllFlowSourceInQuick()) {
            if (flowSource.match(propertyId, area)) {
                flowSource.publishError(new CarPropertyException(propertyId, area));
                break;
            }
        }
    }

    @Override
    public PropertyFlowSource getOrCreateFlowSource(PropKey propKey) {
        return (PropertyFlowSource) super.getOrCreateFlowSource(propKey);
    }

    // ================ CarXXXXManager Abstract Interface ================

    public abstract List<CarPropertyConfig> onLoadConfig() throws CarNotConnectedException;

    public abstract boolean isPropertyAvailable(int propertyId, int area) throws CarNotConnectedException;

    @Override
    public CarFlowSource onCreateFlowSource(PropKey propKey) {
        return new PropertyFlowSource(propKey);
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
                        .getCarPropertyAccess(getUserPreferType());
            }
        }

        public Class<?> getUserPreferType() throws CarNotConnectedException {
            return getPropertyConfig().getPropertyType();
        }

        @Override
        public CarPropertyContext<?> getContext() {
            return (CarPropertyContext<?>) super.getContext();
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
        public Class<?> getUserPreferType() throws CarNotConnectedException {
            if (getType == CarType.VALUE) {
                return getKey().getReturnType();
            }
            return super.getUserPreferType();
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

        public PropertySet(CarPropertyScope scope, Set set) {
            super(set.propId(), resolveArea(set.area(), scope.area()));
        }

        @Override
        public void onInit() {
            super.onInit();
            if (getKey().getParameterCount() != 1) {
                throw new CartrofitGrammarException("Property Set can accept only one parameter " + getKey());
            }
        }

        @Override
        public Object invoke(Object[] parameter) throws CarNotConnectedException {
            getPropertyAccess().set(propertyId, areaId, parameter[0]);
            return null;
        }

        @Override
        public Class<?> getUserPreferType() throws CarNotConnectedException {
            return getKey().getParameterAt(0).getType();
        }
    }

    public static class PropertyTrack extends PropertyAccessCall<Void, CarPropertyValue<?>> {

        private boolean restoreDataWhenTimeout;
        private PropKey propKey;

        public PropertyTrack(CarPropertyScope scope, Track track) {
            super(track.propId(), resolveArea(track.area(), scope.area()));
            this.restoreDataWhenTimeout = track.restoreIfTimeout();
        }

        @Override
        public void onInit() {
            super.onInit();
            if (getKey().isCallbackEntry && getKey().getParameterCount() == 0) {
                throw new CartrofitGrammarException("Must declare one parameter " + getKey());
            }
            propKey = new PropKey(propertyId, areaId, getUserPreferType());
        }

        @Override
        public Flow<CarPropertyValue<?>> onTrackInvoke(Void none) {
            CarPropertyContext<?>.PropertyFlowSource flowSource = getContext()
                    .getOrCreateFlowSource(propKey);
            return Flow.fromSource(flowSource)
                    .catchException(TimeoutException.class, e -> {
                        e.printStackTrace();
                        if (restoreDataWhenTimeout) {
                            CarPropertyValue<?> stickyData = flowSource.getStickyData(true);
                            if (stickyData != null) {
                                flowSource.publish(stickyData);
                            }
                        }
                    });
        }

        @Override
        public Class<?> getUserPreferType() {
            Key key = getKey();
            if (key.isCallbackEntry) {
                for (int i = 0; i < key.getParameterCount(); i++) {
                    Parameter parameter = key.getParameterAt(i);
                    if (parameter.hasNoAnnotation()) {
                        return parameter.getType();
                    }
                }
                return void.class;
            }
            if (key.isAnnotationPresent(Availability.class)) {
                return void.class;
            }
            return key.getReturnAsParameter().getType();
        }
    }
}
