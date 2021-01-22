package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.flow.Flow;
import com.liyangbin.cartrofit.ParameterContext;
import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Timeout;
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

    private boolean convenientTrackMode;
    private Cartrofit.Key convenientTrackKey;
    private boolean confirmTrackResult;
    private Cartrofit.Key convenientTimeoutKey;
    private Call trackCall;
    private Call timeoutCall;

    RegisterCall() {
    }

    RegisterCall(Call trackCall, Cartrofit.Key trackKey, Cartrofit.Key timeoutKey) {
        convenientTrackMode = true;
        this.trackCall = trackCall;
        this.convenientTrackKey = trackKey;
        this.confirmTrackResult = trackKey.getReturnType() == boolean.class;
        this.convenientTimeoutKey = timeoutKey;
        if (timeoutKey != null) {
            timeoutCall = new TimeoutCall(timeoutKey.getAnnotation(Timeout.class).value());
        }
        addChildCall(new Entry(trackCall, timeoutCall));

        Cartrofit.Parameter parameter = getKey().findParameterByAnnotation(Callback.class);
        if (parameter != null) {
            callbackParaIndex = parameter.getDeclaredIndex();
        }
    }

    void addChildCall(Call entryCall, Call returnCall,
                      Call parameterOutCall) {
        addChildCall(new Entry(entryCall, returnCall, (InjectGroupCall) parameterOutCall));
    }

    @Override
    public Call copyByHost(Call host) {
        return convenientTrackMode ? trackCall.copyByHost(host) : super.copyByHost(host);
    }

    static class Entry {
        Call call;
        Call returnCall;
        Call timeoutCall;
        InjectGroupCall injectCall;

        Flow<Void> timerFlow;

        Entry(Call call, Call returnCall, InjectGroupCall injectCall) {
            this.call = call;
            this.returnCall = returnCall;
            this.injectCall = injectCall;
        }

        Entry(Call call, Call timeoutCall) {
            this.call = call;
            this.timeoutCall = timeoutCall;
        }
    }

    @Override
    protected Call asCall(Entry entry) {
        return entry.call;
    }

    @Override
    protected ParameterContext onCreateParameterContext() {
        return convenientTrackMode ? new ConvenientTrackContext(trackCall.getKey())
                : super.onCreateParameterContext();
    }

    private class ConvenientTrackContext extends ParameterContext {

        ConvenientTrackContext(Cartrofit.Key key) {
            super(key);
        }

        @Override
        public Union getParameter(Call target, Union all) {
            if (target == trackCall) {
                return all.exclude(callbackParaIndex);
            } else if (target != null && target == timeoutCall) {
                return Union.ofNull();
            }
            throw new RuntimeException("impossible condition :" + target);
        }
    }

    @Override
    public void onInit() {
        super.onInit();
        Cartrofit.Parameter parameter = getKey().findParameterByAnnotation(Callback.class);
        if (parameter != null) {
            callbackParaIndex = parameter.getDeclaredIndex();
        }
    }

    @Override
    public Cartrofit.Key getKey() {
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

        void register(Union parameter) {
            if (commandObserverList.size() > 0) {
                throw new CartrofitGrammarException("impossible situation");
            }
            for (int i = 0; i < getChildCount(); i++) {
                Entry entry = getChildAt(i);
                InnerObserver observer = new InnerObserver(this, entry);
                commandObserverList.add(observer);

                if (convenientTrackMode && entry.timeoutCall != null && entry.timerFlow == null) {
                    entry.timerFlow = childInvoke(entry.timeoutCall, Union.ofNull());
                }

                observer.registeredFlow = childInvoke(entry.call, parameter);
                observer.registeredFlow.addObserver(observer);
                observer.scheduleTimeoutIfNeeded();
            }
        }

        void unregister() {
            for (int i = 0; i < commandObserverList.size(); i++) {
                InnerObserver observer = commandObserverList.get(i);
                if (observer != null && observer.registeredFlow != null) {
                    observer.registeredFlow.removeObserver(observer);
                    observer.unScheduleTimeoutIfNeeded();
                }
            }
            commandObserverList.clear();
        }

        boolean isExpired(InnerObserver observer) {
            return !commandObserverList.contains(observer);
        }
    }

    private class InnerObserver implements Consumer<Object> {

        Entry entry;
        Call returnCall;
        Object callbackObj;
        RegisterCallbackWrapper callbackWrapper;
        Method method;
        InjectGroupCall parameterInject;
        boolean dispatchProcessing;
        Flow<Object> registeredFlow;

        Consumer<Void> timer;

        InnerObserver(RegisterCallbackWrapper callbackWrapper, Entry entry) {
            this.entry = entry;
            this.method = convenientTrackMode ? convenientTrackKey.method : entry.call.getMethod();
            this.parameterInject = entry.injectCall;
            this.callbackObj = callbackWrapper.callbackObj;
            this.callbackWrapper = callbackWrapper;
        }

        void scheduleTimeoutIfNeeded() {
            if (convenientTrackMode && entry.timeoutCall != null && timer == null) {
                entry.timerFlow.addObserver(timer = aVoid -> {
                    synchronized (RegisterCall.this) {
                        if (callbackWrapper.isExpired(this)) {
                            return;
                        }
                        callbackWrapper.unregister();
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
                });
            }
        }

        void unScheduleTimeoutIfNeeded() {
            if (convenientTrackMode && entry.timeoutCall != null && timer != null) {
                entry.timerFlow.removeObserver(timer);
                timer = null;
            }
        }

        @Override
        public void accept(Object o) {
            if (dispatchProcessing) {
                throw new IllegalStateException("Recursive invocation from:" + entry.call);
            }
            Union union = Union.of(o);
            dispatchProcessing = true;
            synchronized (RegisterCall.this) {
                if (callbackWrapper.isExpired(this)) {
                    // abort
                    return;
                }
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
                    if (convenientTrackMode && (!confirmTrackResult || (Boolean) result)) {
                        unScheduleTimeoutIfNeeded();
                        callbackWrapper.unregister();
                    }

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
}
