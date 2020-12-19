package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Out;

import java.lang.annotation.Annotation;

public class InjectCall extends CallAdapter.Call implements CallAdapter.FieldAccessible {

    ReflectCall reflectCall;
    private InjectInfo dispatchInfo;
    private final int parameterIndex;

    static class InjectInfo {
        boolean get;
        boolean set;
        Object target;
        int parameterIndex;

        InjectInfo copy() {
            InjectInfo copy = new InjectInfo();
            copy.get = this.get;
            copy.set = this.set;
            return copy;
        }

        @Override
        public String toString() {
            return "InjectInfo{" +
                    "get=" + get +
                    ", set=" + set +
                    ", target=" + target +
                    '}';
        }
    }

    InjectCall(int parameterIndex, ReflectCall reflectCall) {
        this.parameterIndex = parameterIndex;
        this.reflectCall = reflectCall;
        reflectCall.setInjectCall(this);
    }

    @Override
    public void onInit(ConverterFactory scopeFactory) {
        reflectCall.init(key, scopeFactory);
        if (key.method != null || key.isCallbackEntry) {
            dispatchInfo = new InjectInfo();
            Annotation[] annotations = key.method.getParameterAnnotations()[parameterIndex];
            for (Annotation parameterAnno : annotations) {
                if (key.isCallbackEntry) {
                    dispatchInfo.get |= parameterAnno instanceof In;
                    dispatchInfo.set |= parameterAnno instanceof Out;
                } else {
                    dispatchInfo.set |= parameterAnno instanceof In;
                    dispatchInfo.get |= parameterAnno instanceof Out;
                }
            }
        }
    }

    @Override
    public CallAdapter.FieldAccessible asFieldAccessible() {
        return key.field != null ? this : null;
    }

    void suppressGetAndExecute(Object object) {
        final boolean oldGetEnable = dispatchInfo.get;
        dispatchInfo.get = false;
        if (shouldExecuteReflectOperation(dispatchInfo)) {
            dispatchInfo.target = object;
            reflectCall.invoke(dispatchInfo);
            dispatchInfo.target = null;
        }
        dispatchInfo.get = oldGetEnable;
    }

    void suppressSetAndExecute(Object object) {
        final boolean oldSetEnable = dispatchInfo.set;
        dispatchInfo.set = false;
        if (shouldExecuteReflectOperation(dispatchInfo)) {
            dispatchInfo.target = object;
            reflectCall.invoke(dispatchInfo);
            dispatchInfo.target = null;
        }
        dispatchInfo.set = oldSetEnable;
    }

    static boolean shouldExecuteReflectOperation(InjectInfo info) {
        return info.set || info.get;
    }

    @Override
    protected Object doInvoke(Object parameter) {
        if (dispatchInfo != null) {
            if (shouldExecuteReflectOperation(dispatchInfo)) {
                dispatchInfo.target = parameter;
                reflectCall.invoke(dispatchInfo);
                dispatchInfo.target = null;
            }
        } else if (parameter instanceof InjectInfo) {
            InjectInfo dispatchedInfo = (InjectInfo) parameter;
            if (shouldExecuteReflectOperation(dispatchedInfo)) {
                InjectInfo info = dispatchedInfo.copy();
                try {
                    info.target = key.field.get(dispatchedInfo.target);
                } catch (IllegalAccessException impossible) {
                    throw new RuntimeException(impossible);
                }
                if (dispatchedInfo.get && info.target == null) {
                    throw new NullPointerException("Can not resolve target:" + key);
                } else if (dispatchedInfo.set && info.target == null) {
                    // TODO: really necessary?
                    try {
                        info.target = key.field.getType().newInstance();
                        key.field.set(dispatchedInfo.target, info.target);
                    } catch (IllegalAccessException | InstantiationException illegalAccessException) {
                        throw new RuntimeException("Do provide a default constructor for:" + key.field.getType());
                    }
                }
                reflectCall.invoke(info);
                info.target = null;
            }
        } else {
            throw new RuntimeException("impossible situation parameter:" + parameter
                    + " from:" + this);
        }
        return null;
    }

    @Override
    public void set(Object target, Object value) throws IllegalAccessException {
        throw new RuntimeException("impossible situation");
    }

    @Override
    public Object get(Object target) throws IllegalAccessException {
        throw new RuntimeException("impossible situation");
    }

//    @Override
//    public CommandType getType() {
//        return CommandType.INJECT;
//    }
//
//    @Override
//    String toCommandString() {
//        String stable = injectClass.getSimpleName();
//        return stable + " " + super.toCommandString();
//    }
}
