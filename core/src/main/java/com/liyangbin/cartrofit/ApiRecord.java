package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.GenerateId;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiRecord<T> {
    private static final String ID_SUFFIX = "Id";

    private HashMap<Integer, Method> selfDependency = new HashMap<>();
    private HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();
    private HashMap<Class<?>, ArrayList<Key>> childrenKeyCache = new HashMap<>();

    final Class<T> clazz;
    final Annotation scopeObj;
    final Class<? extends Annotation> scopeType;

    ApiRecord(Annotation scopeObj, Class<? extends Annotation> scopeType, Class<T> clazz) {
        this.scopeObj = scopeObj;
        this.scopeType = scopeType;
        this.clazz = clazz;

        if (clazz.isAnnotationPresent(GenerateId.class)) {
            try {
                Class<?> selfScopeClass = Class.forName(clazz.getName() + ID_SUFFIX);
                importDependency(selfScopeClass);
            } catch (ClassNotFoundException impossible) {
                throw new IllegalStateException("impossible", impossible);
            }
        }
    }

    Method findMethodById(int id) {
        return selfDependency.get(id);
    }

    @SuppressWarnings("all")
    public int loadIdByCall(Key key) {
        return selfDependencyReverse.getOrDefault(key.method, 0);
    }

    private void importDependency(Class<?> target) {
        try {
            Method method = target.getDeclaredMethod("init", HashMap.class);
            method.invoke(null, selfDependency);
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException(impossible);
        }
        for (Map.Entry<Integer, Method> entry : selfDependency.entrySet()) {
            selfDependencyReverse.put(entry.getValue(), entry.getKey());
        }
    }

    ArrayList<Key> getChildKey(Class<?> targetClass) {
        ArrayList<Key> result = childrenKeyCache.get(targetClass);
        if (result != null) {
            return result;
        }
        result = new ArrayList<>();
        if (targetClass.isInterface()) {
            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                Key childKey = new Key(this, method, true);
                if (!childKey.isInvalid()) {
                    result.add(childKey);
                }
            }
        }
        childrenKeyCache.put(targetClass, result);
        return result;
    }

    ArrayList<Key> getChildKey() {
        return getChildKey(clazz);
    }

    @Override
    public String toString() {
        return "ApiRecord{" +
                "api=" + clazz +
                ", scopeObj='" + scopeObj +
                '}';
    }
}
