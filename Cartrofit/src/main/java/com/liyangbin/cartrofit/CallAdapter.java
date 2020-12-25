package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.funtion.Converter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public abstract class CallAdapter {

    public static final int CATEGORY_SET = 1;
    public static final int CATEGORY_GET = 1 << 1;
    public static final int CATEGORY_TRACK = 1 << 2;
    public static final int CATEGORY_TRACK_EVENT = CATEGORY_TRACK | (1 << 3);

    public static final int CATEGORY_REGISTER = 1 << 4;
    public static final int CATEGORY_RETURN = 1 << 5;
    public static final int CATEGORY_INJECT_IN = 1 << 6;
    public static final int CATEGORY_INJECT_OUT = 1 << 7;
    public static final int CATEGORY_INJECT = CATEGORY_INJECT_IN | CATEGORY_INJECT_OUT;

    public static final int CATEGORY_DEFAULT = 0xffffffff;

    protected Cartrofit.CallInflater mCallInflater;

    public void setCallInflater(Cartrofit.CallInflater callInflater) {
        mCallInflater = callInflater;
    }

    public abstract Object extractScope(Class<?> scopeClass, ConverterFactory scopeConverterSolutionFactory);

    public abstract Call onCreateCall(Object scopeObj, Cartrofit.Key key, int category);

    public static <A extends Annotation> A findScopeByClass(Class<A> annotationClazz, Class<?> clazz) {
        A scope = clazz.getDeclaredAnnotation(annotationClazz);
        if (scope != null) {
            return scope;
        }
        Class<?> enclosingClass = clazz.getEnclosingClass();
        while (enclosingClass != null) {
            scope = enclosingClass.getDeclaredAnnotation(annotationClazz);
            if (scope != null) {
                return scope;
            }
            enclosingClass = enclosingClass.getEnclosingClass();
        }
        return null;
    }

    public interface FieldAccessible {
        void set(Object target, Object value) throws IllegalAccessException;
        Object get(Object target) throws IllegalAccessException;
    }

    public static abstract class Call implements Cloneable {
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
        private Object mTag;

        final void init(Cartrofit.Key key, ConverterFactory scopeFactory) {
            this.key = key;
//            interceptorChain = key.record.getInterceptorByKey(key);

            if (hasCategory(CATEGORY_TRACK)) {
                onReceiveCall = new OnReceiveCall();
                onReceiveCall.init(key, scopeFactory);
                // TODO: set onReceiveCall self attribute by convenient way
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
                    onReceiveCall.enableRestoreData();
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

        public FieldAccessible asFieldAccessible() {
            return null;
        }

        public final Object invoke(Object arg) {
            if (interceptorChain != null) {
                // TODO let sub-class override session creation
                return interceptorChain.doProcess(new Interceptor.InvokeSession(this), arg);
            } else {
                return mapInvoke(arg);
            }
        }

        public final Object mapInvoke(Object parameter) {
            parameter = parameter != null && inputConverter != null ?
                    inputConverter.convert(parameter) : parameter;
            final Object result = doInvoke(parameter);
            if (result instanceof Flow) {
                Flow<?> flowResult = outputConverter != null ?
                        Flow.map((Flow<?>)result, outputConverter) : (Flow<?>) result;
                return flowOutputConverter != null ? flowOutputConverter.convert((Flow<Object>) flowResult) : flowResult;
            } else {
                return result != null && outputConverter != null ?
                        outputConverter.convert(result) : result;
            }
        }

        protected abstract Object doInvoke(Object arg);

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

        void addInterceptor(Interceptor interceptor, boolean toBottom) {
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
    }
}
