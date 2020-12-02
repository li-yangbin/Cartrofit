package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.Get;
import com.liyangbin.cartrofit.annotation.Scope;
import com.liyangbin.cartrofit.annotation.Set;
import com.liyangbin.cartrofit.annotation.Track;

public abstract class CarPropertyDataSource extends AbsDataSource<Scope, CarPropertyDataSource.PropertyDesc> {

    private final String key;

    protected CarPropertyDataSource(String key) {
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
    public PropertyDesc onCreateCommandHandle(Scope scope, Cartrofit.Key key, int category) {
        if ((category & AbsDataSource.CATEGORY_GET) != 0) {
            Get get = key.getAnnotation(Get.class);
            if (get != null) {
                return new PropertyDesc(scope, get);
            }
        }
        if ((category & AbsDataSource.CATEGORY_SET) != 0) {
            Set set = key.getAnnotation(Set.class);
            if (set != null) {
                return new PropertyDesc(scope, set);
            }
        }
        if ((category & AbsDataSource.CATEGORY_TRACK) != 0) {
            Track track = key.getAnnotation(Track.class);
            if (track != null) {
                return new PropertyDesc(scope, track);
            }
        }
        return null;
    }

    @Override
    public Object perform(PropertyDesc description, Object arg) {
        switch (description.type) {
            case PropertyDesc.TYPE_GET:
                return get(description.propertyId, description.areaId, description.carType);
            case PropertyDesc.TYPE_SET:
                set(description.propertyId, description.areaId, arg);
                return null;
            case PropertyDesc.TYPE_TRACK:
                return track(description.propertyId, description.areaId);
        }
        throw new RuntimeException("impossible situation. type:" + description.type);
    }

    @Override
    public boolean hasCategory(PropertyDesc description, int category) {
        return (description.category & category) != 0;
    }

    @Override
    public Class<?> extractValueType(PropertyDesc description) {
        return extractValueType(description.type);
    }

    public abstract Object get(int propertyId, int area, CarType type);

    public abstract <TYPE> void set(int propertyId, int area, TYPE value);

    public abstract Flow<CarPropertyValue<?>> track(int propertyId, int area);

    public abstract Class<?> extractValueType(int propertyId);

    public static class PropertyDesc extends AbsDataSource.Description {
        static final int TYPE_SET = 0;
        static final int TYPE_GET = 1;
        static final int TYPE_TRACK = 2;

        int type;
        int propertyId;
        int areaId;
        int category;

        CarType carType;

        PropertyDesc(Scope scope, Set set) {
            super(set);
            this.type = TYPE_SET;
            this.propertyId = set.id();
            this.areaId = resolveArea(set.area(), scope.area());
            this.carType = CarType.VALUE;
            this.category = AbsDataSource.CATEGORY_SET;
        }

        PropertyDesc(Scope scope, Get get) {
            super(get);
            this.type = TYPE_GET;
            this.propertyId = get.id();
            this.areaId = resolveArea(get.area(), scope.area());
            this.carType = get.type();
            this.category = AbsDataSource.CATEGORY_GET;
        }

        PropertyDesc(Scope scope, Track track) {
            super(track);
            this.type = TYPE_TRACK;
            this.propertyId = track.id();
            this.areaId = resolveArea(track.area(), scope.area());
            this.carType = track.type();
            this.category = AbsDataSource.CATEGORY_TRACK;
        }

        private static int resolveArea(int handleArea, int scopeArea) {
            return handleArea == Scope.DEFAULT_AREA_ID ? scopeArea : handleArea;
        }
    }
}
