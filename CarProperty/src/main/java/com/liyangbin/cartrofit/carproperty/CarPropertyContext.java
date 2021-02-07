package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;

import com.liyangbin.cartrofit.AbsContext;
import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.Key;
import com.liyangbin.cartrofit.SolutionProvider;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowPublisher;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class CarPropertyContext extends AbsContext {

    private static final SolutionProvider CAR_PROPERTY_SOLUTION = new SolutionProvider();
    private static final HashMap<String, Singleton> CAR_SCOPE_CONTEXT = new HashMap<>();

    static {
        Cartrofit.addContextProvider(PropertyScope.class, CarPropertyContext::new);

        CAR_PROPERTY_SOLUTION.create(Get.class)
                .provide((context, category, get, key) -> new PropertyGet(key.getScope(), get));

        CAR_PROPERTY_SOLUTION.create(Set.class)
                .provide((context, category, set, key) -> new PropertySet(key.getScope(), set));

        CAR_PROPERTY_SOLUTION.create(Track.class)
                .provideAndBuildParameter(PropertyTrack.class, (context, category, track, key) ->
                        new PropertyTrack(key.getScope(), track)).takeAny()
                .noAnnotate()
                .output((old, para) -> {
                    if (para.getParameter().getType().equals(CarPropertyEvent.class)) {
                        para.set(old);
                    } else {
                        para.set(old.getValue());
                    }
                    return old;
                }).build()
                .take(boolean.class)
                .annotateWith(Availability.class)
                .output((old, para) -> {
                    para.set(old.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
                    return old;
                }).build()
                .take(int.class)
                .annotateWith(Availability.class)
                .output((old, para) -> {
                    para.set(old.getStatus());
                    return old;
                }).buildAndCommit();
    }

    private static class Singleton {
        private Supplier<CarPropertyContext> initProvider;
        private CarPropertyContext instance;

        Singleton(Supplier<CarPropertyContext> initProvider) {
            this.initProvider = initProvider;
        }

        CarPropertyContext get() {
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        instance = initProvider.get();
                    }
                }
            }
            return instance;
        }
    }

    public static void addScopeProvider(String scope, Supplier<CarPropertyContext> provider) {
        CAR_SCOPE_CONTEXT.put(scope, new Singleton(provider));
    }

    @Override
    public Call getOrCreateCall(Key key, int category, boolean fromCache) {
        if (isDefaultSingleton()) {
            PropertyScope scope = key.getScope();
            Singleton realTarget = CAR_SCOPE_CONTEXT.get(scope.value());
            if (realTarget != null) {
                return realTarget.get().getOrCreateCall(key, category, fromCache);
            } else {
                throw new IllegalStateException("Can not resolve target scope:" + scope.value());
            }
        }
        return super.getOrCreateCall(key, category, fromCache);
    }

    @Override
    public SolutionProvider onProvideCallSolution() {
        return super.onProvideCallSolution().merge(CAR_PROPERTY_SOLUTION);
    }

    public CarPropertyAccess getCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public TypedCarPropertyAccess<?> getTypedCarPropertyAccess(Class<?> type) {
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

    public TypedCarPropertyAccess<Integer> getIntCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public TypedCarPropertyAccess<Boolean> getBooleanCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public TypedCarPropertyAccess<Float> getFloatCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public TypedCarPropertyAccess<Byte> getByteCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public TypedCarPropertyAccess<String> getStringCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    public TypedCarPropertyAccess<byte[]> getByteArrayCarPropertyAccess() {
        throw new RuntimeException("Sub-class should implement this method");
    }

    private enum CarType {
        VALUE, // CarPropertyValue.getValue()
        AVAILABILITY, // CarPropertyValue.getStatus() == CarPropertyValue.STATUS_AVAILABLE
        CONFIG, // CarPropertyConfig
    }

    private static class BuildInValue {
        int intValue;
        int[] intArray;

        boolean booleanValue;
        boolean[] booleanArray;

        long longValue;
        long[] longArray;

        byte byteValue;
        byte[] byteArray;

        float floatValue;
        float[] floatArray;

        String stringValue;
        String[] stringArray;

        static BuildInValue build(CarValue value) {
            if (CarValue.EMPTY_VALUE.equals(value.string())) {
                return null;
            }
            BuildInValue result = new BuildInValue();

            result.intValue = value.Int();
            result.intArray = value.IntArray();

            result.booleanValue = value.Boolean();
            result.booleanArray = value.BooleanArray();

            result.byteValue = value.Byte();
            result.byteArray = value.ByteArray();

            result.floatValue = value.Float();
            result.floatArray = value.FloatArray();

            result.longValue = value.Long();
            result.longArray = value.LongArray();

            result.stringValue = value.string();
            result.stringArray = value.stringArray();

            return result;
        }

        Object extractValue(Class<?> clazz) {
            if (String.class == clazz) {
                return stringValue;
            } else if (String[].class == clazz) {
                return stringArray;
            }
            else if (int.class == clazz || Integer.class == clazz) {
                return intValue;
            } else if (int[].class == clazz) {
                return intArray;
            }
            else if (boolean.class == clazz || Boolean.class == clazz) {
                return booleanValue;
            } else if (boolean[].class == clazz) {
                return booleanArray;
            }
            else if (byte.class == clazz || Byte.class == clazz) {
                return byteValue;
            } else if (byte[].class == clazz) {
                return byteArray;
            }
            else if (float.class == clazz || Float.class == clazz) {
                return floatValue;
            } else if (float[].class == clazz) {
                return floatArray;
            }
            else if (long.class == clazz || Long.class == clazz) {
                return longValue;
            } else if (long[].class == clazz) {
                return longArray;
            }
            else {
                throw new CartrofitGrammarException("invalid type:" + clazz);
            }
        }
    }

    private static int resolveArea(int handleArea, int scopeArea) {
        return handleArea == PropertyScope.DEFAULT_AREA_ID ? scopeArea : handleArea;
    }

    public static abstract class PropertyAccessCall<IN, OUT> extends FixedTypeCall<IN, OUT> {
        TypedCarPropertyAccess<Object> carPropertyTypeAccess;
        CarPropertyAccess carPropertyAccess;

        CarPropertyConfig<?> propertyConfig;
        int propertyId;
        int areaId;

        @Override
        public void onInit() {
            super.onInit();
            carPropertyAccess = getContext().getCarPropertyAccess();
            try {
                propertyConfig = carPropertyAccess.getConfig(propertyId, areaId);
            } catch (CarNotConnectedException connectIssue) {
                throw new RuntimeException(connectIssue);
            }
            carPropertyTypeAccess = (TypedCarPropertyAccess<Object>) getContext()
                    .getTypedCarPropertyAccess(propertyConfig.getPropertyType());
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

        public PropertyGet(PropertyScope scope, Get get) {
            this.propertyId = get.propId();
            this.areaId = resolveArea(get.area(), scope.area());
        }

        @Override
        public void onInit() {
            super.onInit();
            Class<?> returnType = getKey().getReturnType();
            if (returnType.equals(CarPropertyConfig.class)) {
                getType = CarType.CONFIG;
            } else if (AbsContext.classEquals(returnType, boolean.class)
                    && getKey().isAnnotationPresent(Availability.class)) {
                getType = CarType.AVAILABILITY;
            }
        }

        @Override
        public Object mapInvoke(Union parameter) {
            if (getType == CarType.CONFIG) {
                return propertyConfig;
            } else if (getType == CarType.AVAILABILITY) {
                try {
                    return carPropertyAccess.isPropertyAvailable(propertyId, areaId);
                } catch (CarNotConnectedException connectIssue) {
                    throw new RuntimeException(connectIssue);
                }
            } else {
                try {
                    return carPropertyTypeAccess.get(propertyId, areaId);
                } catch (CarNotConnectedException connectIssue) {
                    throw new RuntimeException(connectIssue);
                }
            }
        }
    }

    public static class PropertySet extends PropertyAccessCall<Void, Void> {
        Object buildInSetValue;
        BuildInValue buildInValueUnresolved;

        public PropertySet(PropertyScope scope, Set set) {
            this.propertyId = set.propId();
            this.areaId = resolveArea(set.area(), scope.area());
            buildInValueUnresolved = BuildInValue.build(set.value());
        }

        @Override
        public void onInit() {
            super.onInit();
            if (buildInValueUnresolved != null) {
                buildInSetValue = buildInValueUnresolved.extractValue(propertyConfig.getPropertyType());
                buildInValueUnresolved = null;
            }
        }

        @Override
        public Object mapInvoke(Union parameter) {
            Object toBeSet = buildInSetValue != null ? buildInSetValue : parameter.get(0);
            try {
                carPropertyTypeAccess.set(propertyId, areaId, toBeSet);
            } catch (CarNotConnectedException connectIssue) {
                throw new RuntimeException(connectIssue);
            }
            return null;
        }
    }

    public static class PropertyTrack extends PropertyAccessCall<Void, CarPropertyValue<?>> {

        boolean isSticky;
        boolean restoreDataWhenTimeout;
        FlowPublisher<CarPropertyValue<?>> carValuePublisher;

        public PropertyTrack(PropertyScope scope, Track track) {
            this.propertyId = track.propId();
            this.isSticky = track.sticky();
            this.restoreDataWhenTimeout = track.timeoutRestore();
            this.areaId = resolveArea(track.area(), scope.area());
        }

        @Override
        public void onInit() {
            super.onInit();
            try {
                carValuePublisher = carPropertyAccess.track(propertyId, areaId).publish();
            } catch (CarNotConnectedException connectIssue) {
                throw new RuntimeException(connectIssue);
            }
            if (isSticky) {
                carValuePublisher.enableStickyDispatch(this::onLoadDefaultData);
            }
        }

        public CarPropertyValue<Object> onLoadDefaultData() {
            try {
                return new CarPropertyValue<>(propertyId,
                        areaId, carPropertyTypeAccess.get(propertyId, areaId));
            } catch (CarNotConnectedException connectIssue) {
                throw new RuntimeException(connectIssue);
            }
        }

        @Override
        public Flow<CarPropertyValue<?>> doTrackInvoke(Void none) {
            return carValuePublisher.share().catchException(TimeoutException.class, e -> {
                e.printStackTrace();
                if (restoreDataWhenTimeout) {
                    final CarPropertyValue<?> latestEvent = carValuePublisher.getData();
                    carValuePublisher.injectData(latestEvent != null ? latestEvent : onLoadDefaultData());
                }
            });
        }
    }
}
