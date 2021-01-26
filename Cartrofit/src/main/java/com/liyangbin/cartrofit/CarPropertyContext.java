package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;

import com.liyangbin.cartrofit.annotation.Availability;
import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Restore;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowPublisher;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;

public class CarPropertyContext extends Context {

    private static final HashMap<String, Singleton<Context>> sCarContextMap = new HashMap<>();
    private static final CarPropertyContext INSTANCE = new CarPropertyContext();

    static {
        DefaultDataSource.register();
    }

    public static <T> T fromScope(Class<T> api) {
        Scope scope = api.getDeclaredAnnotation(Scope.class);
        if (scope != null) {
            Singleton<Context> contextProvider = sCarContextMap.get(scope.value());
            if (contextProvider != null) {
                return contextProvider.get().from(api);
            }
        }
        throw new CartrofitGrammarException("Invalid scope for:" + api);
    }

    public static void addCarPropertyHandler(String scopeName, Supplier<Context> initProvider) {
        sCarContextMap.put(scopeName, new Singleton<>(initProvider));
    }

    private static class Singleton<T> {
        private final Supplier<T> initProvider;
        private T instance;

        T get() {
            if (instance == null) {
                synchronized (Singleton.class) {
                    if (instance == null) {
                        instance = initProvider.get();
                    }
                }
            }
            return instance;
        }

        Singleton(Supplier<T> initProvider) {
            this.initProvider = initProvider;
        }
    }

    private CarPropertyContext() {
        super(RootContext.getInstance());
    }

    public static CarPropertyContext getInstance() {
        return INSTANCE;
    }

    @Override
    public Object onExtractScope(Class<?> scopeClass) {
        return scopeClass.getDeclaredAnnotation(Scope.class);
    }

    @Override
    public void onProvideCallSolution(CallSolutionBuilder builder) {
        builder.create(Get.class)
                .provide((category, get, key) -> new PropertyGet(key.getScopeObj(), get));

        builder.create(Set.class)
                .provide((category, set, key) -> new PropertySet(key.getScopeObj(), set));

        builder.create(Track.class)
                .buildParameter(PropertyTrack.class)
                .takeAny()
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
                }).buildAndCommitParameter()
                .provide((category, track, key) -> {
                    PropertyTrack trackCall = new PropertyTrack(key.getScopeObj(), track);
                    Restore restore = key.getAnnotation(Restore.class);
                    if (restore != null && restore.restoreTimeout() > 0) {
                        int setMethodId = restore.value();
                        PropertySet propertySet = (PropertySet) getOrCreateCallById(key,
                                setMethodId, Context.CATEGORY_SET);
                        trackCall.setRestore(propertySet, restore.restoreTimeout());
                    }
                    return trackCall;
                });
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
        return handleArea == Scope.DEFAULT_AREA_ID ? scopeArea : handleArea;
    }

    public static abstract class PropertyAccessCall<IN, OUT> extends FixedTypeCall<IN, OUT> {
        CarPropertyAccess<Object> carPropertyAccess;
        CarPropertyConfigAccess carPropertyConfigAccess;

        CarPropertyConfig<?> propertyConfig;
        int propertyId;
        int areaId;

        @Override
        public void onInit() {
            super.onInit();
            carPropertyConfigAccess = getContext().getContextElement(CarPropertyConfigAccess.class);
            propertyConfig = carPropertyConfigAccess.getConfig(propertyId, areaId);
            carPropertyAccess = getContext().getContextElement(propertyConfig.getPropertyType());
        }
    }

    public static class PropertyGet extends PropertyAccessCall<Void, Void> {
        CarType getType = CarType.VALUE;

        PropertyGet(Scope scope, Get get) {
            this.propertyId = get.id();
            this.areaId = resolveArea(get.area(), scope.area());
        }

        @Override
        public void onInit() {
            super.onInit();
            Class<?> returnType = getKey().getReturnType();
            if (returnType.equals(CarPropertyConfig.class)) {
                getType = CarType.CONFIG;
            } else if (Context.classEquals(returnType, boolean.class)
                    && getKey().isAnnotationPresent(Availability.class)) {
                getType = CarType.AVAILABILITY;
            }
        }

        @Override
        public Object mapInvoke(Union parameter) {
            if (getType == CarType.CONFIG) {
                return propertyConfig;
            } else if (getType == CarType.AVAILABILITY) {
                return carPropertyAccess.isPropertyAvailable(propertyId, areaId);
            } else {
                return carPropertyAccess.get(propertyId, areaId);
            }
        }
    }

    public static class PropertySet extends PropertyAccessCall<Void, Void> implements Flow.FlowSource<Object> {
        Object buildInSetValue;
        BuildInValue buildInValueUnresolved;
        ArrayList<Flow.Injector<Object>> onInvokeListener = new ArrayList<>();

        PropertySet(Scope scope, Set set) {
            this.propertyId = set.id();
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
            for (int i = 0; i < onInvokeListener.size(); i++) {
                onInvokeListener.get(i).send(toBeSet);
            }
            carPropertyAccess.set(propertyId, areaId, toBeSet);
            return null;
        }

        @Override
        public void startWithInjector(Flow.Injector<Object> injector) {
            onInvokeListener.add(injector);
        }

        @Override
        public void finishWithInjector(Flow.Injector<Object> injector) {
            onInvokeListener.remove(injector);
        }

        @Override
        public boolean isHot() {
            return true;
        }
    }

    public static class PropertyTrack extends PropertyAccessCall<Void, CarPropertyValue<Object>> {

        boolean isSticky;
        PropertySet propertySet;
        int restoreMillis;
        FlowPublisher<CarPropertyValue<Object>> carValuePublisher;

        PropertyTrack(Scope scope, Track track) {
            this.propertyId = track.id();
            this.isSticky = track.sticky();
            this.areaId = resolveArea(track.area(), scope.area());
        }

        void setRestore(PropertySet propertySet, int restoreMillis) {
            this.propertySet = propertySet;
            this.restoreMillis = restoreMillis;
        }

        @Override
        public void onInit() {
            super.onInit();
            carValuePublisher = carPropertyAccess.track(propertyId, areaId).publish();
            if (isSticky) {
                carValuePublisher.enableStickyDispatch(this::onLoadDefaultData);
            }
            carValuePublisher.start();

            if (propertySet != null) {
                Flow.fromSource(propertySet)
                    .switchMap(obj -> {
                        CarPropertyValue<Object> latestEvent = carValuePublisher.getData();
                        // TODO: test
                        return carValuePublisher.share()
                            .take(carValue -> Objects.equals(obj, carValue.getValue()), 1)
                            .timeout(restoreMillis, () -> {
                                carValuePublisher.injectData(latestEvent != null ? latestEvent : onLoadDefaultData());
                            });
                    }).subscribeWithoutResultConcern();
            }
        }

        protected CarPropertyValue<Object> onLoadDefaultData() {
            return new CarPropertyValue<>(propertyId,
                    areaId, carPropertyAccess.get(propertyId, areaId));
        }

        @Override
        protected Flow<CarPropertyValue<Object>> doTrackInvoke(Void none) {
            return carValuePublisher.share();
        }
    }
}
