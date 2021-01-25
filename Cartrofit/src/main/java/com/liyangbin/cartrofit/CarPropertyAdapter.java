package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;

import com.liyangbin.cartrofit.annotation.Availability;
import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Config;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Restore;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowPublisher;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Objects;

public abstract class CarPropertyAdapter extends CallAdapter {

    private final String key;

    public CarPropertyAdapter(String key) {
        this.key = key;
    }

    @Override
    public Scope extractScope(Class<?> apiClass) {
        Scope scope = findScopeByClass(Scope.class, apiClass);
        if (scope != null && scope.value().equals(key)) {
            return scope;
        }
        return null;
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
                .provide((category, track, key) -> new PropertyTrack(key.getScopeObj(), track));
    }

    public abstract Object get(int propertyId, int area, CarType type);

    public abstract void set(int propertyId, int area, Object value);

    public abstract Flow<CarPropertyValue<?>> track(int propertyId, int area);

    public abstract Class<?> extractValueType(int propertyId);

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

    public class PropertyGet extends Call {
        int propertyId;
        int areaId;
        CarType getType = CarType.VALUE;

        PropertyGet(Scope scope, Get get) {
            this.propertyId = get.id();
            this.areaId = resolveArea(get.area(), scope.area());
        }

        @Override
        public void onInit() {
            super.onInit();
            Annotation[] annotations = getKey().getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof Availability) {
                    getType = CarType.AVAILABILITY;
                    break;
                } else if (annotation instanceof Config) {
                    getType = CarType.CONFIG;
                    break;
                }
            }
        }

        @Override
        public Object mapInvoke(Union parameter) {
            return CarPropertyAdapter.this.get(propertyId, areaId, getType);
        }
    }

    public class PropertySet extends Call implements Flow.FlowSource<Object> {

        int propertyId;
        int areaId;
        Object buildInSetValue;
        ArrayList<Flow.Injector<Object>> onInvokeListener = new ArrayList<>();

        PropertySet(Scope scope, Set set) {
            this.propertyId = set.id();
            this.areaId = resolveArea(set.area(), scope.area());
            BuildInValue buildInValue = BuildInValue.build(set.value());
            if (buildInValue != null) {
                buildInSetValue = buildInValue.extractValue(extractValueType(propertyId));
            }
        }

        @Override
        public Object mapInvoke(Union parameter) {
            Object toBeSet = buildInSetValue != null ? buildInSetValue : parameter.get(0);
            for (int i = 0; i < onInvokeListener.size(); i++) {
                onInvokeListener.get(i).send(toBeSet);
            }
            CarPropertyAdapter.this.set(propertyId, areaId, toBeSet);
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

    private class PropertyTrack extends FixedTypeCall<Void, CarPropertyValue<?>> {

        int propertyId;
        int areaId;
        boolean isSticky;
        FlowPublisher<CarPropertyValue<?>> carValuePublisher;

        PropertyTrack(Scope scope, Track track) {
            this.propertyId = track.id();
            this.isSticky = track.sticky();
            this.areaId = resolveArea(track.area(), scope.area());
        }

        @Override
        public void onInit() {
            super.onInit();

            carValuePublisher = CarPropertyAdapter.this.track(propertyId, areaId).publish();
            if (isSticky) {
                carValuePublisher.enableStickyDispatch(this::onLoadDefaultData);
            }
            carValuePublisher.start();

            Restore restore = getKey().getAnnotation(Restore.class);
            if (restore != null && restore.restoreTimeout() > 0) {
                int setMethodId = restore.value();
                Call call = getOrCreateCallById(getKey(), setMethodId, CallAdapter.CATEGORY_SET, false);
                Flow.fromSource((PropertySet) call)
                    .switchMap(obj -> {
                        CarPropertyValue<?> latestEvent = carValuePublisher.getData();
                        // TODO: test
                        return carValuePublisher.share()
                            .take(carValue -> Objects.equals(obj, carValue.getValue()), 1)
                            .timeout(restore.value(), () -> {
                                carValuePublisher.injectData(latestEvent != null ? latestEvent : onLoadDefaultData());
                            });
                    }).subscribeWithoutResultConcern();
            }
        }

        protected CarPropertyValue<?> onLoadDefaultData() {
            return new CarPropertyValue<>(propertyId,
                    areaId, CarPropertyAdapter.this.get(propertyId, areaId, CarType.VALUE));
        }

        @Override
        protected Flow<CarPropertyValue<?>> doTrackInvoke(Void none) {
            return carValuePublisher.share();
        }
    }
}
