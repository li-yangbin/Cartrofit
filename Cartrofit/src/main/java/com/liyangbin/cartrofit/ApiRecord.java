package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.GenerateId;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ApiRecord<T> {
    private static final String ID_SUFFIX = "Id";

    private HashMap<Integer, Method> selfDependency = new HashMap<>();
    private HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();

    final Class<T> clazz;
    final Annotation scopeObj;
    final Class<? extends Annotation> scopeType;

    ApiRecord(Annotation scopeObj, Class<T> clazz) {
        this.scopeObj = scopeObj;
        this.scopeType = scopeObj.annotationType();
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

    ArrayList<Key> getChildKey(Key parentKey) {
        ArrayList<Key> result = new ArrayList<>();
        if (clazz.isInterface()) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                Key childKey = new Key(this, method, true);
                if (!childKey.isInvalid()) {
                    childKey.setDelegateKey(parentKey);
                    result.add(childKey);
                }
            }
        } else {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                Key childKey = new Key(this, field);
                if (!childKey.isInvalid()) {
                    childKey.setDelegateKey(parentKey);
                    result.add(childKey);
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "ApiRecord{" +
                "api=" + clazz +
                ", scopeObj='" + scopeObj +
                '}';
    }
}
