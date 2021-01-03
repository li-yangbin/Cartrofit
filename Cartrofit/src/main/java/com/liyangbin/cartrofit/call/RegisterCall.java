package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.ConverterFactory;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;

public class RegisterCall extends CallGroup<RegisterCall.Entry> {

    private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();
    private int callbackParaIndex;

    void addChildCall(Call entryCall, Call returnCall,
                      Call parameterOutCall) {
        addChildCall(new Entry(entryCall, returnCall, (InjectGroupCall) parameterOutCall));
    }

    static class Entry {
        Call call;
        Call returnCall;
        InjectGroupCall injectCall;
        Flow<Object> registeredCall;

        Entry(Call call, Call returnCall, InjectGroupCall injectCall) {
            this.call = call;
            this.returnCall = returnCall;
            this.injectCall = injectCall;
        }
    }

    @Override
    protected Call asCall(Entry entry) {
        return entry.call;
    }

    @Override
    public void onInit(ConverterFactory scopeFactory) {
        super.onInit(scopeFactory);
        Cartrofit.Parameter parameter = key.findParameterByAnnotation(Callback.class);
        if (parameter != null) {
            callbackParaIndex = parameter.getDeclaredIndex();
        }
    }

    @Override
    public Object mapInvoke(Union<?> parameter) {
        final Object callback = parameter.get(callbackParaIndex);
        if (callbackWrapperMapper.containsKey(
                Objects.requireNonNull(callback, "callback can not be null"))) {
            return null;
        }
        RegisterCallbackWrapper wrapper = new RegisterCallbackWrapper(callback);
        callbackWrapperMapper.put(callback, wrapper);
        wrapper.register(parameter);
        return null;
    }

//    @Override
//    public CommandType getType() {
//        return CommandType.REGISTER;
//    }

    void untrack(Object callback) {
        RegisterCallbackWrapper wrapper = callbackWrapperMapper.remove(callback);
        if (wrapper != null) {
            wrapper.unregister();
        }
    }

    private class RegisterCallbackWrapper {
        Object callbackObj;
        ArrayList<InnerObserver> commandObserverList = new ArrayList<>();

        RegisterCallbackWrapper(Object callbackObj) {
            this.callbackObj = callbackObj;
        }

        void register(Union<?> parameter) {
            if (commandObserverList.size() > 0) {
                throw new CartrofitGrammarException("impossible situation");
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                InnerObserver observer = new InnerObserver(entry, callbackObj);
                commandObserverList.add(observer);
                entry.registeredCall = childInvoke(entry.call, parameter);
                entry.registeredCall.addObserver(observer);
            }
        }

        void unregister() {
            for (int i = 0; i < commandObserverList.size(); i++) {
                Entry entry = getChildAt(i);
                InnerObserver observer = commandObserverList.get(i);
                if (observer != null && entry.registeredCall != null) {
                    entry.registeredCall.removeObserver(observer);
                    entry.registeredCall = null;
                }
            }
            commandObserverList.clear();
        }
    }

    private static class InnerObserver implements Consumer<Object> {

        Call flowCall;
        Call returnCall;
        Object callbackObj;
        Method method;
        InjectGroupCall parameterInject;
        boolean dispatchProcessing;

        InnerObserver(Entry entry, Object callbackObj) {
            this.flowCall = entry.call;
            this.returnCall = entry.returnCall;
            this.callbackObj = callbackObj;
            this.parameterInject = entry.injectCall;
        }

        @Override
        public void accept(Object o) {
            if (dispatchProcessing) {
                throw new IllegalStateException("Recursive invocation from:" + flowCall);
            }
            Union<?> union = Union.of(o);
            dispatchProcessing = true;
            try {
                int parameterCount = flowCall.getKey().getParameterCount();
                int injectCount = parameterInject != null ? parameterInject.getChildCount() : 0;
                Object[] parameters = new Object[parameterCount + injectCount];
                for (int i = 0, j = 0; i < parameters.length; i++) {
                    InjectCall injectCall = parameterInject != null ?
                            parameterInject.findInjectCallAtParameterIndex(i) : null;
                    if (injectCall != null) {
                        try {
                            parameters[i] = injectCall.targetClass.newInstance();
                        } catch (InstantiationException e) {
                            e.printStackTrace();
                        }
                    } else {
                        parameters[i] = union.get(j++);
                    }
                }

                if (parameterInject != null) {
                    parameterInject.suppressSetAndInvoke(union);
                }

                Object result = method.invoke(callbackObj, parameters);

                if (parameterInject != null) {
                    parameterInject.suppressGetAndInvoke(union);
                }

                if (returnCall != null) {
                    returnCall.invoke(Union.of(result));
                }
            } catch (InvocationTargetException invokeExp) {
                if (invokeExp.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) invokeExp.getCause();
                } else {
                    throw new RuntimeException("Callback invoke error", invokeExp.getCause());
                }
            } catch (IllegalAccessException illegalAccessException) {
                throw new RuntimeException("Impossible", illegalAccessException);
            } finally {
                dispatchProcessing = false;
            }
        }
    }
}
