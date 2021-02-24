package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.Key;
import com.liyangbin.cartrofit.Parameter;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RegisterCall extends CallGroup<RegisterCall.Entry> {

    private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();
    private int callbackParaIndex;

    private boolean coldTrackMode;
    private Key coldTrackKey;
    private HashMap<Class<?>, Key> coldErrorKeyMap;
    private Key coldCompleteKey;

    public RegisterCall() {
        // register call
    }

    public RegisterCall(Call trackCall, Key callbackKey,
                        HashMap<Class<?>, Key> errorKeyMap, Key completeKey) {
        // register call transformed from track call
        coldTrackMode = true;
        this.coldTrackKey = callbackKey;
        this.coldErrorKeyMap = errorKeyMap;
        this.coldCompleteKey = completeKey;
        addChildCall(new Entry(trackCall));
    }

    public void addChildCall(Call entryCall, Call returnCall,
                      Call parameterOutCall) {
        addChildCall(new Entry(entryCall, returnCall, (InjectGroupCall) parameterOutCall));
    }

    static class Entry {
        Call call;
        Call returnCall;
        InjectGroupCall injectCall;

        Entry(Call call, Call returnCall, InjectGroupCall injectCall) {
            this.call = call;
            this.returnCall = returnCall;
            this.injectCall = injectCall;
        }

        Entry(Call call) {
            this.call = call;
        }
    }

    @Override
    protected Call asCall(Entry entry) {
        return entry.call;
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
    public Object mapInvoke(Union parameter) {
        final Object callback = Objects.requireNonNull(
                parameter.get(callbackParaIndex), "callback can not be null");
        if (coldTrackMode) {
            new RegisterCallbackWrapper(callback).register(parameter);
        } else {
            if (callbackWrapperMapper.containsKey(callback)) {
                return null;
            }
            RegisterCallbackWrapper wrapper = new RegisterCallbackWrapper(callback);
            callbackWrapperMapper.put(callback, wrapper);
            wrapper.register(parameter);
        }
        return null;
    }

    void untrack(Object callback) {
        RegisterCallbackWrapper wrapper = callbackWrapperMapper.remove(callback);
        if (wrapper != null) {
            wrapper.unregister();
        }
    }

    private class RegisterCallbackWrapper {
        Object callbackObj;
        ArrayList<Flow<Object>> commandFlowList = new ArrayList<>();

        RegisterCallbackWrapper(Object callbackObj) {
            this.callbackObj = callbackObj;
        }

        void register(Union parameter) {
            if (commandFlowList.size() > 0) {
                throw new CartrofitGrammarException("impossible situation");
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                Flow<Object> registeredFlow = childInvoke(entry.call, parameter);
                if (registeredFlow.isHot() && coldTrackMode) {
                    throw new CartrofitGrammarException("Can not use cold register mode on hot flow source");
                }
                InnerObserver observer = new InnerObserver(coldTrackMode ? coldTrackKey
                        : entry.call.getKey(), callbackObj, registeredFlow);
                commandFlowList.add(registeredFlow);

                registeredFlow.subscribe(observer);
            }
        }

        void unregister() {
            for (int i = 0; i < commandFlowList.size(); i++) {
                Flow<Object> registeredFlow = commandFlowList.get(i);
                registeredFlow.stopSubscribe();
            }
            commandFlowList.clear();
        }
    }

    private class InnerObserver implements FlowConsumer<Object> {

        Key callbackKey;
        Object callbackObj;
        Flow<?> upStream;
        boolean dispatchProcessing;

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
            } else {
                FlowConsumer.defaultThrow(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (coldCompleteKey != null) {
                safeInvoke(coldCompleteKey, callbackObj);
            }
        }

        @Override
        public void accept(Object o) {
            if (dispatchProcessing) {
                throw new IllegalStateException("Recursive invocation from " + callbackKey);
            }
            Union union = Union.of(o);
            dispatchProcessing = true;
            int parameterCount = callbackKey.getParameterCount();
            Object[] parameters = new Object[parameterCount];
            for (int i = 0, j = 0; i < parameters.length; i++) {
                parameters[i] = union.get(j++);
            }
            safeInvoke(callbackKey, callbackObj, () -> dispatchProcessing = false, parameters);
        }
    }

    private static void safeInvoke(Key key, Object obj, Object... parameters) {
        safeInvoke(key, obj, null, parameters);
    }

    private static void safeInvoke(Key key, Object obj, Runnable finallyAction,
                                   Object... parameters) {
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
        } finally {
            if (finallyAction != null) {
                finallyAction.run();
            }
        }
    }
}
