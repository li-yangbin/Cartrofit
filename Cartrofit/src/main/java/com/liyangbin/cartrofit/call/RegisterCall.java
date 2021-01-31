package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.Key;
import com.liyangbin.cartrofit.Parameter;
import com.liyangbin.cartrofit.ParameterContext;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Timeout;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.flow.FlowConsumer;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public class RegisterCall extends CallGroup<RegisterCall.Entry> {

    private final HashMap<Object, RegisterCallbackWrapper> callbackWrapperMapper = new HashMap<>();
    private int callbackParaIndex;

    private boolean convenientTrackMode;
    private Key convenientTrackKey;
    private Key convenientTimeoutKey;
    private Call trackCall;
    private int timeOutMillis;

    public RegisterCall() {
    }

    public RegisterCall(Call trackCall, Key trackKey, Key timeoutKey) {
        convenientTrackMode = true;
        this.trackCall = trackCall;
        this.convenientTrackKey = trackKey;
        this.convenientTimeoutKey = timeoutKey;
        if (timeoutKey != null) {
            timeOutMillis = timeoutKey.getAnnotation(Timeout.class).value();
        }
        addChildCall(new Entry(trackCall));

        Parameter parameter = getKey().findParameterByAnnotation(Callback.class);
        if (parameter != null) {
            callbackParaIndex = parameter.getDeclaredIndex();
        }
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
    public Key getKey() {
        return convenientTrackMode ? trackCall.getKey() : super.getKey();
    }

    @Override
    public Object mapInvoke(Union parameter) {
        final Object callback = Objects.requireNonNull(
                parameter.get(callbackParaIndex), "callback can not be null");
        if (convenientTrackMode) {
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
                if (registeredFlow.isHot() && convenientTrackMode) {
                    throw new CartrofitGrammarException("Can not use convenientTrack on hot flow source");
                }
                InnerObserver observer = new InnerObserver(this, entry);
                if (convenientTrackMode && convenientTimeoutKey != null) {
                    registeredFlow = registeredFlow.timeout(timeOutMillis);
                }
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

        Entry entry;
        Call returnCall;
        Object callbackObj;
        RegisterCallbackWrapper callbackWrapper;
        Method method;
        InjectGroupCall parameterInject;
        boolean dispatchProcessing;

        InnerObserver(RegisterCallbackWrapper callbackWrapper, Entry entry) {
            this.entry = entry;
            this.method = convenientTrackMode ? convenientTrackKey.method : entry.call.getMethod();
            this.parameterInject = entry.injectCall;
            this.callbackObj = callbackWrapper.callbackObj;
            this.callbackWrapper = callbackWrapper;
        }

        @Override
        public void onError(Throwable throwable) {
            if (throwable instanceof TimeoutException) {
                onTimeOut();
            } else {
                FlowConsumer.defaultThrow(throwable);
            }
        }

        void onTimeOut() {
            try {
                convenientTimeoutKey.method.invoke(callbackObj);
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

        @Override
        public void onComplete() {
            untrack(callbackObj);
        }

        //        void scheduleTimeoutIfNeeded() {
//            if (convenientTrackMode && entry.timeoutCall != null && timer == null) {
//                entry.timerFlow.addObserver(timer = aVoid -> {
//                    synchronized (RegisterCall.this) {
//                        if (callbackWrapper.isExpired(this)) {
//                            return;
//                        }
//                        callbackWrapper.unregister();
//
//                    }
//                });
//            }
//        }
//
//        void unScheduleTimeoutIfNeeded() {
//            if (convenientTrackMode && entry.timeoutCall != null && timer != null) {
//                entry.timerFlow.removeObserver(timer);
//                timer = null;
//            }
//        }

        @Override
        public void accept(Object o) {
            if (dispatchProcessing) {
                throw new IllegalStateException("Recursive invocation from:" + entry.call);
            }
            Union union = Union.of(o);
            dispatchProcessing = true;
            try {
                int parameterCount = entry.call.getKey().getParameterCount();
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
