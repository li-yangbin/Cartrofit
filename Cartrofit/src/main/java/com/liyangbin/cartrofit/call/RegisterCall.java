package com.liyangbin.cartrofit.call;

import android.os.Build;

import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;

public class RegisterCall extends CallGroup<RegisterCall.Entry> {

    private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();

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
    public Object doInvoke(Object callback) {
        if (callbackWrapperMapper.containsKey(
                Objects.requireNonNull(callback, "callback can not be null"))) {
            return null;
        }
        RegisterCallbackWrapper wrapper = new RegisterCallbackWrapper(callback);
        callbackWrapperMapper.put(callback, wrapper);
        wrapper.register();
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

        void register() {
            if (commandObserverList.size() > 0) {
                throw new CartrofitGrammarException("impossible situation");
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                InnerObserver observer = new InnerObserver(entry, callbackObj);
                commandObserverList.add(observer);
                if (entry.registeredCall == null) {
                    entry.registeredCall = (Flow<Object>) entry.call.invoke(null);
                }
                entry.registeredCall.addObserver(observer);
            }
        }

        void unregister() {
            for (int i = 0; i < commandObserverList.size(); i++) {
                Entry entry = getChildAt(i);
                InnerObserver observer = commandObserverList.get(i);
                if (observer != null && entry.registeredCall != null) {
                    entry.registeredCall.removeObserver(observer);
                }
            }
            commandObserverList.clear();
        }
    }

    private static int getParameterCount(Method method) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return method.getParameterTypes().length;
        } else {
            return method.getParameterCount();
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
                int parameterCount = flowCall.key.getParameterCount();
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
                    returnCall.invoke(result);
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
