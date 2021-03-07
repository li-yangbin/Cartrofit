package com.liyangbin.cartrofit.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

import androidx.lifecycle.LiveData;

public class FlowPublisher<T> {

    private final Flow<T> upStream;
    private volatile T data;
    private boolean hasData;
    private boolean publishStarted;
    private boolean startWhenConnected;
    private boolean dispatchStickyDataEnable;
    private InitialDataProvider<T> initialDataProvider;
    private ArrayList<SharedFlow> downStreamList = new ArrayList<>();
    private final HashMap<Consumer<T>, Flow<T>> listenerMap = new HashMap<>();

    FlowPublisher(Flow<T> upStream) {
        this.upStream = upStream;
    }

    public interface InitialDataProvider<T> {
        T get() throws Throwable;
    }

    public FlowPublisher<T> setDispatchStickyDataEnable(boolean dispatchStickyDataEnable,
                                                        InitialDataProvider<T> initialDataProvider) {
        this.dispatchStickyDataEnable = dispatchStickyDataEnable;
        this.initialDataProvider = initialDataProvider;
        return this;
    }

    public FlowPublisher<T> start() {
        if (!publishStarted) {
            publishStarted = true;
            startWhenConnected = false;

            upStream.subscribe(new PublishConsumer());
        }
        return this;
    }

    public FlowPublisher<T> startIfConnected() {
        if (!publishStarted) {
            startWhenConnected = true;
        }
        return this;
    }

    public void stop() {
        if (publishStarted) {
            publishStarted = false;
            upStream.stopSubscribe();
        }
    }

    public void addSubscriber(FlowConsumer<T> consumer) {
        Flow<T> sharedFlow;
        synchronized (this) {
            if (listenerMap.containsKey(consumer)) {
                return;
            }
            sharedFlow = share();
            listenerMap.put(consumer, sharedFlow);
        }
        sharedFlow.subscribe(consumer);
    }

    public void removeSubscriber(FlowConsumer<T> consumer) {
        Flow<T> sharedFlow;
        synchronized (this) {
            sharedFlow = listenerMap.remove(consumer);
        }
        if (sharedFlow != null) {
            sharedFlow.stopSubscribe();
        }
    }

    void onClientSubscribed(SharedFlow flow) {
        boolean doSubscribe = false;
        synchronized (this) {
            downStreamList = new ArrayList<>(downStreamList);
            downStreamList.add(flow);
            if (!publishStarted && startWhenConnected && downStreamList.size() == 1) {
                doSubscribe = true;
                publishStarted = true;
            }
        }
        if (doSubscribe) {
            upStream.subscribe(new PublishConsumer());
        }

        if (dispatchStickyDataEnable) {
            synchronized (this) {
                if (!hasData && initialDataProvider != null) {
                    try {
                        data = initialDataProvider.get();
                    } catch (Throwable throwable) {
                        flow.consumer.onError(throwable);
                        return;
                    }
                    hasData = true;
                }
                if (!hasData) {
                    return;
                }
            }
            flow.consumer.accept(data);
        }
    }

    synchronized void onClientSubscribeStopped(SharedFlow flow) {
        boolean doStopSubscribe = false;
        synchronized (this) {
            boolean existAndRemove = downStreamList.contains(flow);
            if (existAndRemove) {
                downStreamList = new ArrayList<>(downStreamList);
                downStreamList.remove(flow);
            }
            if (existAndRemove && publishStarted
                    && downStreamList.size() == 0 && startWhenConnected) {
                doStopSubscribe = true;
                publishStarted = false;
            }
        }
        if (doStopSubscribe) {
            upStream.stopSubscribe();
        }
    }

    public Flow<T> share() {
        return new SharedFlow();
    }

    public final LiveData<T> toLiveData() {
        return share().toLiveData();
    }

    public T getData(T defaultValue) {
        return hasData ? data : defaultValue;
    }

    public T getData() {
        if (hasData) {
            return data;
        }
        ArrayList<SharedFlow> safeErrorReceiver = null;
        Throwable error = null;
        synchronized (this) {
            if (!hasData && initialDataProvider != null) {
                try {
                    data = initialDataProvider.get();
                    hasData = true;
                } catch (Throwable throwable) {
                    safeErrorReceiver = downStreamList;
                    error = throwable;
                }
            }
        }
        if (safeErrorReceiver != null) {
            for (int i = 0; i < safeErrorReceiver.size(); i++) {
                safeErrorReceiver.get(i).consumer.onError(error);
            }
        }
        return data;
    }

    public boolean hasData() {
        return hasData;
    }

    public void injectData(T t) {
        ArrayList<SharedFlow> safeFlowList;
        synchronized (FlowPublisher.this) {
            data = t;
            hasData = true;
            safeFlowList = downStreamList;
        }
        for (int i = 0; i < safeFlowList.size(); i++) {
            safeFlowList.get(i).consumer.accept(t);
        }
    }

    private class PublishConsumer implements FlowConsumer<T> {

        @Override
        public void accept(T t) {
            FlowPublisher.this.injectData(t);
        }

        @Override
        public void onComplete() {
            ArrayList<SharedFlow> safeFlowList;
            synchronized (FlowPublisher.this) {
                safeFlowList = downStreamList;
                downStreamList = new ArrayList<>();
                publishStarted = false;
                hasData = false;
                data = null;
            }
            for (int i = 0; i < safeFlowList.size(); i++) {
                safeFlowList.get(i).consumer.onComplete();
            }
        }

        @Override
        public void onCancel() {
            ArrayList<SharedFlow> safeFlowList;
            synchronized (FlowPublisher.this) {
                safeFlowList = downStreamList;
            }
            for (int i = 0; i < safeFlowList.size(); i++) {
                safeFlowList.get(i).consumer.onCancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            ArrayList<SharedFlow> safeFlowList;
            synchronized (FlowPublisher.this) {
                safeFlowList = downStreamList;
            }
            for (int i = 0; i < safeFlowList.size(); i++) {
                safeFlowList.get(i).consumer.onError(throwable);
            }
        }
    }

    private class SharedFlow extends Flow<T> {

        FlowConsumer<T> consumer;

        @Override
        protected void onSubscribeStarted(FlowConsumer<T> consumer) {
            this.consumer = consumer;
            onClientSubscribed(this);
        }

        @Override
        protected void onSubscribeStopped() {
            onClientSubscribeStopped(this);
        }

        @Override
        public boolean isHot() {
            return true;
        }
    }
}
