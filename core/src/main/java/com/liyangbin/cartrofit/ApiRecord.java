package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Process;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ApiRecord<T> {
    private static final String ID_SUFFIX = "Id";

    private final HashMap<Integer, Method> selfDependency = new HashMap<>();
    private final HashMap<Class<?>, HashMap<String, CallbackInvoker>> callbackInvokerMap = new HashMap<>();
    private final HashMap<Method, Integer> selfDependencyReverse = new HashMap<>();
    private final HashMap<Class<?>, ArrayList<Key>> childrenKeyCache = new HashMap<>();

    final Class<T> clazz;
    final Annotation scopeObj;
    final Class<? extends Annotation> scopeType;

    ApiRecord(Annotation scopeObj, Class<? extends Annotation> scopeType, Class<T> clazz) {
        this.scopeObj = scopeObj;
        this.scopeType = scopeType;
        this.clazz = clazz;

        if (clazz.isAnnotationPresent(Process.class)) {
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

    CallbackInvoker findCallbackInvoker(Class<?> callbackType, String methodName) {
        HashMap<String, CallbackInvoker> methodMap = callbackInvokerMap.get(callbackType);
        return methodMap != null ? methodMap.get(methodName) : null;
    }

    @SuppressWarnings("all")
    public int loadIdByCall(Key key) {
        return selfDependencyReverse.getOrDefault(key.method, 0);
    }

    private void importDependency(Class<?> target) {
        try {
            Method method = target.getDeclaredMethod("_init", HashMap.class, HashMap.class);
            method.invoke(null, selfDependency, callbackInvokerMap);
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
