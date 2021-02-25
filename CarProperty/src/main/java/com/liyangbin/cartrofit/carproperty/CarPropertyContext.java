package com.liyangbin.cartrofit.carproperty;

import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.FixedTypeCall;
import com.liyangbin.cartrofit.solution.SolutionProvider;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowPublisher;

import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public abstract class CarPropertyContext extends CartrofitContext {

    private static final SolutionProvider CAR_PROPERTY_SOLUTION = new SolutionProvider();
    private static final Cartrofit.ContextFactory<CarPropertyScope> SCOPE_FACTORY =
            Cartrofit.createSingletonFactory(CarPropertyScope.class, CarPropertyScope::value);

    static {
        CAR_PROPERTY_SOLUTION.create(Get.class)
                .provide((context, category, get, key) -> new PropertyGet(key.getScope(), get));

        CAR_PROPERTY_SOLUTION.create(Set.class)
                .provide((context, category, set, key) -> new PropertySet(key.getScope(), set));

        CAR_PROPERTY_SOLUTION.create(Track.class)
                .provideAndBuildParameter(PropertyTrack.class, (context, category, track, key) ->
                        new PropertyTrack(key.getScope(), track))
                .takeAny()
                .noAnnotate()
                .output((old, para) -> {
                    if (para.getParameter().getType().equals(CarPropertyValue.class)) {
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

    public static void registerScopeProvider(String scope, Supplier<CarPropertyContext> provider) {
        SCOPE_FACTORY.register(scope, provider);
    }

    @Override
    public SolutionProvider onProvideCallSolution() {
        return super.onProvideCallSolution().merge(CAR_PROPERTY_SOLUTION);
    }

    public boolean onPropertySetIntercept(int propId, int area, Object value) {
        return false;
    }

    public Object onPropertyGetIntercept(int propId, int area, CarType carType) {
        return null;
    }

    public abstract CarPropertyAccess getCarPropertyAccess();

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

        public PropertyGet(CarPropertyScope scope, Get get) {
            this.propertyId = get.propId();
            this.areaId = resolveArea(get.area(), scope.area());
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
        public Object invoke(Object[] parameter) {
            Object interceptedValue = getContext().onPropertyGetIntercept(propertyId, areaId, getType);
            if (interceptedValue != null) {
                return interceptedValue;
            }
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

        public PropertySet(CarPropertyScope scope, Set set) {
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
        public Object invoke(Object[] parameter) {
            Object toBeSet = buildInSetValue != null ? buildInSetValue : parameter[0];
            if (getContext().onPropertySetIntercept(propertyId, areaId, toBeSet)) {
                return null;
            }
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

        public PropertyTrack(CarPropertyScope scope, Track track) {
            this.propertyId = track.propId();
            this.isSticky = track.sticky();
            this.restoreDataWhenTimeout = track.restoreIfTimeout();
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
            carValuePublisher.setDispatchStickyDataEnable(isSticky, this::onLoadDefaultData);
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
                    carValuePublisher.injectData(carValuePublisher.getData());
                }
            })
            // TODO: consider having app handle CarPropertyException event
            .catchException(CarPropertyException.class, Throwable::printStackTrace);
        }
    }
}
