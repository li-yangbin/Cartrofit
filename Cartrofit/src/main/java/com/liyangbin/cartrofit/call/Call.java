package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.ConverterFactory;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.FlowConverter;
import com.liyangbin.cartrofit.funtion.Converter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static com.liyangbin.cartrofit.CallAdapter.CATEGORY_GET;
import static com.liyangbin.cartrofit.CallAdapter.CATEGORY_SET;
import static com.liyangbin.cartrofit.CallAdapter.CATEGORY_TRACK;

public abstract class Call implements Cloneable {
    private static final Timer sTimeOutTimer = new Timer("timeout-tracker");
    private static final long TIMEOUT_DELAY = 1500;

    protected Cartrofit.Key key;
    protected int category;
    InterceptorChain interceptorChain;
    private ConverterFactory converterFactory;
    protected Converter inputConverter;
    protected FlowConverter<?> flowOutputConverter;
    protected Converter outputConverter;

    private ArrayList<Call> restoreSchedulerList = new ArrayList<>();
    private Call restoreReceiver;
    private TimerTask task;
    private OnReceiveCall onReceiveCall;
    private boolean stickyTrackSupport;
    private Object mTag;

    public final void init(Cartrofit.Key key, ConverterFactory scopeFactory) {
        this.key = key;

        if (hasCategory(CATEGORY_TRACK)) {
            onReceiveCall = new OnReceiveCall(this);
        }

        if (key.field != null) {
            key.field.setAccessible(true);
        }

        onInit(converterFactory = new ConverterFactory(scopeFactory));

        if (hasCategory(CATEGORY_SET)) {
            inputConverter = converterFactory.findInputConverterByKey(key);
        }
        boolean isGet = hasCategory(CATEGORY_GET);
        boolean flowTrack = hasCategory(CATEGORY_TRACK);
        if (isGet || flowTrack) {
            if (flowTrack) {
                outputConverter = converterFactory.findOutputConverterByKey(key, true);
                flowOutputConverter = converterFactory.findFlowConverter(key);
            } else {
                outputConverter = converterFactory.findOutputConverterByKey(key, false);
            }
        }
    }

    public void enableStickyTrack() {
        if (onReceiveCall != null) {
            stickyTrackSupport = true;
            onReceiveCall.enableSaveLatestData();
        }
    }

    boolean isStickyTrackEnable() {
        return stickyTrackSupport;
    }

    public void setRestoreTarget(Call targetCall) {
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
                onReceiveCall.addInterceptor((command, parameter) -> {
                    synchronized (Call.this) {
                        if (task != null) {
                            task.cancel();
                            task = null;
                        }
                    }
                    return command.invoke(parameter);
                }, false);
            }
            restoreScheduler.addInterceptor((command, parameter) -> {
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
                return command.invoke(parameter);
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

    public final Object invoke(Object arg) {
        if (interceptorChain != null) {
            return interceptorChain.doProcess(onCreateInvokeSession(), arg);
        } else {
            return mapInvoke(arg);
        }
    }

    @SuppressWarnings("unchecked")
    Object mapInvoke(Object parameter) {
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

    protected abstract Object doInvoke(Object arg);

    protected Interceptor.InvokeSession onCreateInvokeSession() {
        return new Interceptor.InvokeSession(this);
    }

    public Call copyByHost(Call host) {
        try {
            Call call = (Call) clone();
            call.inputConverter = null;
            call.outputConverter = null;
            return call;
        } catch (CloneNotSupportedException error) {
            throw new RuntimeException(error);
        }
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
}
