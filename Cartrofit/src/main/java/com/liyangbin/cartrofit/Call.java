package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Token;
import com.liyangbin.cartrofit.funtion.Converter;
import com.liyangbin.cartrofit.funtion.Union;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import static com.liyangbin.cartrofit.CallAdapter.CATEGORY_GET;
import static com.liyangbin.cartrofit.CallAdapter.CATEGORY_SET;
import static com.liyangbin.cartrofit.CallAdapter.CATEGORY_TRACK;

public abstract class Call implements Cloneable {
    static final Timer sTimeOutTimer = new Timer("timeout-tracker");
    private static final long TIMEOUT_DELAY = 1500;

    protected Cartrofit.Key key;
    protected int category;
    InterceptorChain interceptorChain;
    private boolean inOutConvertDisabled;
    private ConverterFactory converterFactory;
    private Converter<Union, ?> inputConverter;
    private FlowConverter<?> flowOutputConverter;
    private Converter outputConverter;

    private ArrayList<Call> restoreSchedulerList = new ArrayList<>();
    private Call restoreReceiver;
    private CallGroup<?> parentCall;
    private TimerTask task;
    private OnReceiveCall onReceiveCall;
    private boolean stickyTrackSupport;
    private boolean parameterRequired = true;
    private Object mTag;
    private List<String> tokenList;

    final void init(Cartrofit.Key key, Cartrofit.Key rootKey,
                    ConverterFactory scopeFactory) {
        this.key = key;

        if (hasCategory(CATEGORY_TRACK)) {
            onReceiveCall = new OnReceiveCall(this);
        }

        if (key.field != null) {
            key.field.setAccessible(true);
        }

        onInit(converterFactory = new ConverterFactory(scopeFactory));

        Cartrofit.Key parameterKey = key.isCallbackEntry || key.field != null ? rootKey : key;

        if (!inOutConvertDisabled && hasCategory(CATEGORY_SET)) {
            inputConverter = converterFactory.findInputConverterByKey(parameterKey);
        }
        boolean flowTrack = hasCategory(CATEGORY_TRACK);
        if (flowTrack) {
            flowOutputConverter = converterFactory.findFlowConverter(parameterKey);
        }
        if (!inOutConvertDisabled) {
            if (flowTrack) {
                outputConverter = converterFactory.findOutputConverterByKey(parameterKey, true);
            } else if (hasCategory(CATEGORY_GET)) {
                outputConverter = converterFactory.findOutputConverterByKey(parameterKey, false);
            }
        }
    }

    public Cartrofit.Key getKey() {
        return key;
    }

    protected void disableParameterRequirement() {
        parameterRequired = false;
    }

    public boolean isParameterRequired() {
        return parameterRequired || (!key.isCallbackEntry && key.getParameterCount() > 0);
    }

    void enableStickyTrack() {
        if (onReceiveCall != null) {
            stickyTrackSupport = true;
            onReceiveCall.enableSaveLatestData();
        }
    }

    boolean isStickyTrackEnable() {
        return stickyTrackSupport;
    }

    void setTokenList(Token token) {
        tokenList = Arrays.asList(token.value());
    }

    public void addToken(String token) {
        if (tokenList == null) {
            tokenList = new ArrayList<>();
        }
        tokenList.add(token);
    }

    public boolean hasToken(String token) {
        return tokenList != null && tokenList.contains(token);
    }

    void setRestoreTarget(Call targetCall) {
        if (targetCall.hasCategory(CATEGORY_SET) && hasCategory(CATEGORY_TRACK)) {
            attachScheduler(targetCall);
            targetCall.attachReceiver(this);
        } else if (targetCall.hasCategory(CATEGORY_TRACK) && hasCategory(CATEGORY_SET)) {
            attachReceiver(targetCall);
            targetCall.attachScheduler(this);
        }
    }

    void attachScheduler(Call restoreScheduler) {
        if (!restoreSchedulerList.contains(restoreScheduler)) {
            restoreSchedulerList.add(restoreScheduler);
            if (restoreSchedulerList.size() == 1) {
                onReceiveCall.enableSaveLatestData();
                onReceiveCall.addInterceptor((call, parameter) -> {
                    synchronized (Call.this) {
                        if (task != null) {
                            task.cancel();
                            task = null;
                        }
                    }
                    return call.invoke(parameter);
                }, false);
            }
            restoreScheduler.addInterceptor((call, parameter) -> {
                if (onReceiveCall.hasHistoricalData()) {
                    synchronized (Call.this) {
                        if (task != null) {
                            task.cancel();
                        }
                        sTimeOutTimer.schedule(task = new TimerTask() {
                            @Override
                            public void run() {
                                synchronized (Call.this) {
                                    onReceiveCall.restoreDispatch();
                                }
                            }
                        }, TIMEOUT_DELAY);
                    }
                }
                return call.invoke(parameter);
            }, true);
        }
    }

    void attachReceiver(Call restoreReceiver) {
        this.restoreReceiver = restoreReceiver;
    }

    public void onInit(ConverterFactory scopeFactory) {
    }

    protected final void addCategory(int category) {
        this.category |= category;
    }

    public CallAdapter.FieldAccessible asFieldAccessible() {
        return null;
    }

    public Object onLoadStickyValue() {
        return null;
    }

    public final Object invoke(Union parameter) {
        if (interceptorChain != null) {
            return interceptorChain.doProcess(onCreateInvokeSession(), parameter);
        } else {
            try {
                return mapInvoke(parameter);
            } finally {
                parameter.recycle();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Object mapInvoke(Union parameter) {
        parameter = parameter != null && inputConverter != null ?
                inputConverter.convert(parameter) : parameter;
        final Object result = doInvoke(parameter);
        if (result instanceof Flow) {
            Flow<?> flowResult = (Flow<?>) result;
            if (stickyTrackSupport) {
                flowResult = flowResult.sticky();
            }
            flowResult = flowResult.untilReceive(onReceiveCall);
            flowResult = outputConverter != null ? flowResult.map(outputConverter) : flowResult;
            return flowOutputConverter != null ?
                    flowOutputConverter.convert((Flow<Object>) flowResult) : flowResult;
        } else {
            return result != null && outputConverter != null ?
                    outputConverter.convert(result) : result;
        }
    }

    void attachParent(CallGroup<?> parent) {
        parentCall = parent;
    }

    public CallGroup<?> getParent() {
        return parentCall;
    }

    protected Object doInvoke(Object arg) {
        return null;
    }

    protected Interceptor.InvokeSession onCreateInvokeSession() {
        return new Interceptor.InvokeSession(this);
    }

    public Call copyByHost(Call host) {
        try {
            Call call = (Call) clone();
            call.flowOutputConverter = null;
            host.disableInOutConvert();
            return call;
        } catch (CloneNotSupportedException error) {
            throw new RuntimeException(error);
        }
    }

    private void disableInOutConvert() {
        inOutConvertDisabled = true;
        inputConverter = outputConverter = null;
    }

    public final Method getMethod() {
        return key.method;
    }

    public void addInterceptor(Interceptor interceptor, boolean toBottom) {
        if (interceptorChain != null) {
            if (toBottom) {
                interceptorChain.addInterceptorToBottom(interceptor);
            } else {
                interceptorChain.addInterceptor(interceptor);
            }
        } else {
            interceptorChain = new InterceptorChain(interceptor);
        }
    }

    public boolean hasCategory(int category) {
        return (this.category & category) != 0;
    }

    public void setTag(Object obj) {
        mTag = obj;
    }

    @SuppressWarnings("unchecked")
    public <T> T getTag() {
        return (T) mTag;
    }

    public final int getId() {
        return key.getId();
    }

    static class OnReceiveCall extends Call {

        private final WeakHashMap<FlowWrapper, Union> lastTransactData = new WeakHashMap<>();
        private boolean keepLatestData;

        private final Call trackCall;

        OnReceiveCall(Call trackCall) {
            this.trackCall = trackCall;
        }

        @Override
        public Object mapInvoke(Union parameter) {
            return null;
        }

        @Override
        protected Object doInvoke(Object arg) {
            return null;
        }

        void enableSaveLatestData() {
            keepLatestData = true;
        }

        void restoreDispatch() {
            keepLatestData = false;
            for (Map.Entry<FlowWrapper, Union> entry : lastTransactData.entrySet()) {
                invokeWithFlow(entry.getKey(), entry.getValue());
            }
            keepLatestData = true;
        }

        boolean hasHistoricalData() {
            return !lastTransactData.isEmpty();
        }

        Object getHistoricalData(FlowWrapper flowWrapper) {
            return lastTransactData.get(flowWrapper);
        }

        Object loadInitialData() {
            return trackCall.isStickyTrackEnable() ? trackCall.onLoadStickyValue() : null;
        }

        final void invokeWithFlow(FlowWrapper callFrom, Union transact) {
            if (interceptorChain != null) {
                interceptorChain.doProcess(new OnReceiveCallSession(callFrom), transact);
            } else {
                if (keepLatestData) {
                    lastTransactData.put(callFrom, transact);
                }
                callFrom.onReceiveComplete(OnReceiveCall.this, transact);
            }
        }

        private class OnReceiveCallSession extends Interceptor.InvokeSession {

            final FlowWrapper callFrom;

            OnReceiveCallSession(FlowWrapper callFrom) {
                super(trackCall);
                this.callFrom = callFrom;
            }

            @Override
            public void onInterceptComplete(Union parameter) {
                if (keepLatestData) {
                    lastTransactData.put(callFrom, parameter);
                }
                this.callFrom.onReceiveComplete(OnReceiveCall.this, parameter);
            }

            @Override
            public boolean isReceive() {
                return true;
            }
        }
    }
}
