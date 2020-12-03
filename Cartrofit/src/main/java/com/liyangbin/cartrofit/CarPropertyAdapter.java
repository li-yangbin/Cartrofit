package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.CarValue;
import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;

public abstract class CarPropertyAdapter extends CallAdapter<Scope, CarPropertyAdapter.PropertyCall> {

    private final String key;

    protected CarPropertyAdapter(String key) {
        this.key = key;
    }

    @Override
    public Scope getScopeInfo(Class<?> scopeClass) {
        return scopeClass.getDeclaredAnnotation(Scope.class);
    }

    @Override
    public boolean isInterested(Scope scope) {
        return scope.value().equals(this.key);
    }

    @Override
    public PropertyCall onCreateCall(Scope scope, Cartrofit.Key key, int category) {
        if ((category & CallAdapter.CATEGORY_GET) != 0) {
            Get get = key.getAnnotation(Get.class);
            if (get != null) {
                return new PropertyCall(scope, get);
            }
        }
        if ((category & CallAdapter.CATEGORY_SET) != 0) {
            Set set = key.getAnnotation(Set.class);
            if (set != null) {
                return new PropertyCall(scope, set);
            }
        }
        if ((category & CallAdapter.CATEGORY_TRACK) != 0) {
            Track track = key.getAnnotation(Track.class);
            if (track != null) {
                return new PropertyCall(scope, track);
            }
        }
        return null;
    }

    @Override
    public Object invoke(PropertyCall call, Object arg) {
        switch (call.type) {
            case PropertyCall.TYPE_GET:
                return get(call.propertyId, call.areaId, call.carType);
            case PropertyCall.TYPE_SET:
                set(call.propertyId, call.areaId,
                        call.buildInSetValue != null ? call.buildInSetValue : arg);
                return null;
            case PropertyCall.TYPE_TRACK:
                Flow<CarPropertyValue<?>> flow = track(call.propertyId, call.areaId);
                switch (call.carType) {
                    case VALUE:
                        return new MediatorFlow<>(flow, CarPropertyValue::getValue);
                    case AVAILABILITY:
                        return new MediatorFlow<>(flow, value -> value != null
                                && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE);
                    default:
                        return flow;
                }
        }
        throw new RuntimeException("impossible situation. type:" + call.type);
    }

    @Override
    public boolean hasCategory(PropertyCall call, int category) {
        return (call.category & category) != 0;
    }

    @Override
    public Class<?> extractValueType(PropertyCall call) {
        return extractValueType(call.type);
    }

    public abstract Object get(int propertyId, int area, CarType type);

    public abstract <TYPE> void set(int propertyId, int area, TYPE value);

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

    public class PropertyCall extends CallAdapter<Scope, PropertyCall>.Call {
        static final int TYPE_SET = 0;
        static final int TYPE_GET = 1;
        static final int TYPE_TRACK = 2;

        int type;
        int propertyId;
        int areaId;
        int category;
        Object buildInSetValue;

        CarType carType;

        PropertyCall(Scope scope, Set set) {
            super(set);
            this.type = TYPE_SET;
            this.propertyId = set.id();
            this.areaId = resolveArea(set.area(), scope.area());
            this.carType = CarType.VALUE;
            this.category = CallAdapter.CATEGORY_SET;
            BuildInValue buildInValue = BuildInValue.build(set.value());
            if (buildInValue != null) {
                buildInSetValue = buildInValue.extractValue(extractValueType(propertyId));
            }
        }

        PropertyCall(Scope scope, Get get) {
            super(get);
            this.type = TYPE_GET;
            this.propertyId = get.id();
            this.areaId = resolveArea(get.area(), scope.area());
            this.carType = get.type();
            this.category = CallAdapter.CATEGORY_GET;
        }

        PropertyCall(Scope scope, Track track) {
            super(track);
            this.type = TYPE_TRACK;
            this.propertyId = track.id();
            this.areaId = resolveArea(track.area(), scope.area());
            this.carType = track.type();
            this.category = CallAdapter.CATEGORY_TRACK;
            if (carType == CarType.NONE) {
                this.category |= CallAdapter.CATEGORY_TRACK_EVENT;
            }
        }

        private int resolveArea(int handleArea, int scopeArea) {
            return handleArea == Scope.DEFAULT_AREA_ID ? scopeArea : handleArea;
        }
    }
}
