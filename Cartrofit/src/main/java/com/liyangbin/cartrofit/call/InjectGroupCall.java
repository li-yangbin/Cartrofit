package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.ParameterContext;
import com.liyangbin.cartrofit.annotation.In;
import com.liyangbin.cartrofit.annotation.Out;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.Arrays;
import java.util.HashMap;

public class InjectGroupCall extends CallGroup<InjectGroupCall.Entry> {

    private final Entry[] parameterInject;
    private boolean getSuppressed;
    private boolean setSuppressed;

    InjectGroupCall(int parameterCount) {
        parameterInject = new Entry[parameterCount];
    }

    void addChildInjectCall(int parameterIndex, InjectCall call, boolean doSet, boolean doGet) {
        if (doGet || doSet) {
            addChildCall(new Entry(parameterIndex, call, doGet, doSet));
        }
    }

    @Override
    public void addChildCall(Entry call) {
        super.addChildCall(call);
        parameterInject[call.parameterIndex] = call;
    }

    @Override
    protected Call asCall(Entry entry) {
        return entry.call;
    }

    @Override
    public void removeChildCall(Entry call) {
        super.removeChildCall(call);
        parameterInject[call.parameterIndex] = null;
    }

    void suppressGetAndInvoke(Union<?> object) {
        getSuppressed = true;
        invoke(object);
        getSuppressed = false;
    }

    void suppressSetAndInvoke(Union<?> object) {
        setSuppressed = true;
        invoke(object);
        setSuppressed = false;
    }

    @Override
    public InjectContext getParameterContext() {
        return (InjectContext) super.getParameterContext();
    }

    @Override
    protected ParameterContext onCreateParameterContext() {
        return new InjectContext();
    }

    public class InjectContext extends ParameterContext {

        int injectTargetIndex;
        Object[] targetContainer;
        HashMap<InjectCall, Object> contextTarget = new HashMap<>();

        InjectContext() {
            super(key);
            int size = 0;
            for (int i = 0; i < key.getParameterCount(); i++) {
                Cartrofit.Parameter parameter = key.getParameterAt(i);
                if (parameter.isAnnotationPresent(In.class)
                        || parameter.isAnnotationPresent(Out.class)) {
                    injectTargetIndex |= (1 << i);
                    size++;
                }
            }
            targetContainer = new Object[size];
        }

        public boolean doGet(InjectCall child) {
            if (getSuppressed) {
                return false;
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                if (entry.call == child) {
                    return entry.get;
                }
            }
            return false;
        }

        public boolean doSet(InjectCall child) {
            if (setSuppressed) {
                return false;
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                if (entry.call == child) {
                    return entry.set;
                }
            }
            return false;
        }

        void attachParameter(Union<?> parameter) {
            contextTarget.clear();
            if (parameter != null) {
                for (int i = 0,j = 0; i < parameter.getCount(); i++) {
                    if ((injectTargetIndex & i) != 0) {
                        targetContainer[j++] = parameter.get(i);
                    }
                }
            } else {
                Arrays.fill(targetContainer, null);
            }
        }

        public Object getTarget(InjectCall child) throws IllegalAccessException {
            Object obj = contextTarget.get(child);
            if (obj == null) {
                contextTarget.put(child, obj = resolveContextTarget(child));
            }
            return obj;
        }

        private Object resolveContextTarget(InjectCall childCall) throws IllegalAccessException {
            if (childCall.getParent() == InjectGroupCall.this) {
                for (int i = 0; i < getChildCount(); i++) {
                    if (getChildAt(i).call == childCall) {
                        return targetContainer[i];
                    }
                }
                throw new RuntimeException("logic impossible");
            }
            Object containerObject = resolveContextTarget((InjectCall) childCall.getParent());
            if (containerObject != null) {
                return childCall.asFieldAccessible().get(containerObject);
            }
            throw new NullPointerException("Can not resolve target " + childCall.getKey());
        }
    }

    InjectCall findInjectCallAtParameterIndex(int parameterIndex) {
        InjectGroupCall.Entry entry = parameterIndex >= 0
                && parameterIndex < parameterInject.length ? parameterInject[parameterIndex] : null;
        return entry != null ? entry.call : null;
    }

    static class Entry {
        InjectCall call;
        int parameterIndex;

        boolean get;
        boolean set;

        Entry(int index, InjectCall call, boolean get, boolean set) {
            this.parameterIndex = index;
            this.call = call;
            this.get = get;
            this.set = set;
        }
    }

    @Override
    public Object mapInvoke(Union<?> parameter) {
        final int elementCount = getChildCount();
        getParameterContext().attachParameter(parameter);
        for (int i = 0; i < elementCount; i++) {
            childInvoke(getChildAt(i).call, parameter);
        }
        getParameterContext().attachParameter(null);
        return null;
    }
}
