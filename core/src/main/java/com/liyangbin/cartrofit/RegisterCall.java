package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RegisterCall extends CallGroup<Call> {

    private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();
    private int callbackParaIndex;

    private boolean coldTrackMode;
    private Key coldTrackKey;
    private HashMap<Class<?>, Key> coldErrorKeyMap;
    private Key coldCompleteKey;

    RegisterCall() {
        // register call
    }

    RegisterCall(Call trackCall, Key callbackKey,
                 HashMap<Class<?>, Key> errorKeyMap, Key completeKey) {
        // register call transformed from track call
        coldTrackMode = true;
        this.coldTrackKey = callbackKey;
        this.coldErrorKeyMap = errorKeyMap;
        this.coldCompleteKey = completeKey;
        addChildCall(trackCall);
    }

    Key getColdTrackKey() {
        return coldTrackKey;
    }

    @Override
    protected Call asCall(Call entry) {
        return entry;
    }

    public boolean isColdTrackMode() {
        return coldTrackMode;
    }

    @Override
    public void onInit() {
        super.onInit();
        Parameter parameter = getKey().findParameterByAnnotation(Callback.class);
        if (parameter != null) {
            callbackParaIndex = parameter.getDeclaredIndex();
        }
    }

    @Override
    public Object invoke(Object[] parameter) throws Throwable {
        final Object callback = Objects.requireNonNull(
                parameter[callbackParaIndex], "callback can not be null");
        RegisterCallbackWrapper wrapper;
        synchronized (callbackWrapperMapper) {
            if (callbackWrapperMapper.containsKey(callback)) {
                return null;
            }
            wrapper = new RegisterCallbackWrapper(callback);
            callbackWrapperMapper.put(callback, wrapper);
        }
        wrapper.register(parameter);
        return null;
    }

    void untrack(Object callback) {
        RegisterCallbackWrapper wrapper;
        synchronized (callbackWrapperMapper) {
            wrapper = callbackWrapperMapper.remove(callback);
        }
        if (wrapper != null) {
            wrapper.unregister();
        }
    }

    private class RegisterCallbackWrapper {
        Object callbackObj;
        ArrayList<Flow<Object[]>> commandFlowList = new ArrayList<>();

        RegisterCallbackWrapper(Object callbackObj) {
            this.callbackObj = callbackObj;
        }

        void register(Object[] parameter) throws Throwable {
            if (commandFlowList.size() > 0) {
                throw new CartrofitGrammarException("impossible situation");
            }

            for (int i = 0; i < getChildCount(); i++) {
                Call call = getChildAt(i);
                Flow<Object[]> registeredFlow = childInvoke(call, parameter);
                commandFlowList.add(registeredFlow);
            }

            for (int i = 0; i < getChildCount(); i++) {
                Call call = getChildAt(i);
                Flow<Object[]> registeredFlow = commandFlowList.get(i);
                registeredFlow.subscribe(new InnerObserver(coldTrackMode ? coldTrackKey
                        : call.getKey(), callbackObj, registeredFlow));
            }

        }

        void unregister() {
            for (int i = 0; i < commandFlowList.size(); i++) {
                Flow<Object[]> registeredFlow = commandFlowList.get(i);
                registeredFlow.stopSubscribe();
            }
            commandFlowList.clear();
        }
    }

    private class InnerObserver implements FlowConsumer<Object[]> {

        Key callbackKey;
        Object callbackObj;
        Flow<?> upStream;

        InnerObserver(Key callbackKey, Object callbackObj, Flow<?> upStream) {
            this.callbackKey = callbackKey;
            this.callbackObj = callbackObj;
            this.upStream = upStream;
        }

        @Override
        public void onError(Throwable throwable) {
            Key key = null;
            if (coldErrorKeyMap != null) {
                key = coldErrorKeyMap.get(throwable.getClass());
                if (key == null) {
                    for (Map.Entry<Class<?>, Key> entry : coldErrorKeyMap.entrySet()) {
                        if (entry.getKey().isInstance(throwable)) {
                            key = entry.getValue();
                            break;
                        }
                    }
                }
            }
            if (key != null) {
                safeInvoke(key, callbackObj, throwable);
            } else if (!handleFlowCallbackException(throwable, callbackObj)) {
                FlowConsumer.defaultThrow(throwable,
                        getKey().getParameterAt(callbackParaIndex).getType());
            }
        }

        @Override
        public void onComplete() {
            synchronized (callbackWrapperMapper) {
                callbackWrapperMapper.remove(callbackKey);
            }
            if (coldCompleteKey != null) {
                safeInvoke(coldCompleteKey, callbackObj);
            }
            callbackKey = null;
        }

        @Override
        public void accept(Object[] parameters) {
            safeInvoke(callbackKey, callbackObj, parameters);
        }
    }

    private static void safeInvoke(Key key, Object obj, Object... parameters) {
        try {
            key.method.invoke(obj, parameters);
        } catch (IllegalArgumentException parameterError) {
            throw new RuntimeException("Parameter type mismatch. Expected:" + key.method
                    + " Actual:" + Arrays.toString(parameters));
        } catch (InvocationTargetException invokeExp) {
            if (invokeExp.getCause() instanceof RuntimeException) {
                throw (RuntimeException) invokeExp.getCause();
            } else {
                throw new RuntimeException("Callback invoke error", invokeExp.getCause());
            }
        } catch (IllegalAccessException illegalAccessException) {
            throw new RuntimeException("Impossible", illegalAccessException);
        }
    }
}
