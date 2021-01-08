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
import java.util.Objects;

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

    void suppressGetAndInvoke(Union object) {
        getSuppressed = true;
        invoke(object);
        getSuppressed = false;
    }

    void suppressSetAndInvoke(Union object) {
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
            Call injectParent = child.getParent();
            while (injectParent.getParent() != null) {
                injectParent = injectParent.getParent();
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                if (entry.call == injectParent) {
                    return entry.get;
                }
            }
            return false;
        }

        public boolean doSet(InjectCall child) {
            if (setSuppressed) {
                return false;
            }
            Call injectParent = child.getParent();
            while (injectParent.getParent() != null) {
                injectParent = injectParent.getParent();
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                if (entry.call == injectParent) {
                    return entry.set;
                }
            }
            return false;
        }

        void attachParameter(Union parameter) {
            contextTarget.clear();
            if (parameter != null) {
                for (int i = 0,j = 0; i < parameter.getCount(); i++) {
                    if ((injectTargetIndex & i) != 0) {
                        final Object target = Objects.requireNonNull(parameter.get(i));
                        contextTarget.put(getChildAt(j).call, target);
                        targetContainer[j] = target;
                        j++;
                    }
                }
            } else {
                Arrays.fill(targetContainer, null);
            }
        }

        public Object getTarget(InjectCall child) throws IllegalAccessException {
            if (child == null) {
                return null;
            }
            Object obj = contextTarget.get(child);
            if (obj != null) {
                return obj;
            }

            Object containerObject = getTarget((InjectCall) child.getParent());
            if (containerObject != null) {
                obj = child.asFieldAccessible().get(containerObject);
                contextTarget.put(child, obj);
                return obj;
            }
            throw new NullPointerException("Can not resolve target " + child.getKey());
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
    public Object mapInvoke(Union parameter) {
        final int elementCount = getChildCount();
        getParameterContext().attachParameter(parameter);
        for (int i = 0; i < elementCount; i++) {
            childInvoke(getChildAt(i).call, parameter);
        }
        getParameterContext().attachParameter(null);
        return null;
    }
}
