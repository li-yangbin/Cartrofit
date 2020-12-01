package com.liyangbin.cartrofit;

import android.car.hardware.CarPropertyValue;

import com.liyangbin.cartrofit.annotation.Scope;

import java.lang.annotation.Annotation;

public abstract class CarPropertyDataSource extends AbsDataSource {

    @Override
    public PropertyScope getScopeInfo(Class<?> scopeClass) {
        Scope scope = scopeClass.getDeclaredAnnotation(Scope.class);
        return scope != null ? new PropertyScope(scope) : null;
    }

    @Override
    public Command onCreateCommand(Cartrofit.Key key) {
        return null;
    }

    public abstract Object get(int key, int area, CarType type);

    public abstract <TYPE> void set(int key, int area, TYPE value);

    public abstract Flow<CarPropertyValue<?>> track(int key, int area);

    public abstract Class<?> extractValueType(int key);

    public static class PropertyScope extends ScopeInfo {
        String scopeKey;
        Class<?> scopeOnCreateClass;
        int defaultArea;

        public PropertyScope(Scope scope) {
            scopeKey = scope.value();
            scopeOnCreateClass = scope.onCreate();
            defaultArea = scope.area();
        }
    }
}
