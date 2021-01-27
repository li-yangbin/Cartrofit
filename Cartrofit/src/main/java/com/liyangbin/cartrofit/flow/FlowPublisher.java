package com.liyangbin.cartrofit.flow;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FlowPublisher<T> {

    private final Flow<T> upStream;
    private volatile T data;
    private boolean hasData;
    private boolean publishStarted;
    private boolean startWhenConnected;
    private boolean dispatchStickyDataEnable;
    private Supplier<T> initialStickyDataProvider;
    private final ArrayList<SharedFlow> downStreamList = new ArrayList<>();
    private final HashMap<Consumer<T>, Flow<T>> listenerMap = new HashMap<>();

    public FlowPublisher(Flow<T> upStream, boolean startWhenConnected) {
        this.upStream = upStream;
        this.startWhenConnected = startWhenConnected;
    }

    public void enableStickyDispatch(Supplier<T> initialDataProvider) {
        dispatchStickyDataEnable = true;
        this.initialStickyDataProvider = initialDataProvider;
    }

    public void start() {
        if (!publishStarted) {
            publishStarted = true;

            upStream.subscribe(new PublishConsumer());
            startWhenConnected = false;
        }
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
        if (dispatchStickyDataEnable) {
            synchronized (this) {
                if (!hasData && initialStickyDataProvider != null) {
                    data = initialStickyDataProvider.get();
                    hasData = true;
                }
                if (!hasData) {
                    return;
                }
            }
            consumer.accept(data);
        }
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
            downStreamList.add(flow);
            if (!publishStarted && startWhenConnected && downStreamList.size() == 1) {
                doSubscribe = true;
                publishStarted = true;
            }
        }
        if (doSubscribe) {
            upStream.subscribe(new PublishConsumer());
        }
    }

    synchronized void onClientSubscribeStopped(SharedFlow flow) {
        boolean doStopSubscribe = false;
        synchronized (this) {
            if (downStreamList.remove(flow) && publishStarted
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

    public T getData() {
        synchronized (this) {
            if (!hasData) {
                data = initialStickyDataProvider.get();
                hasData = true;
            }
        }
        return data;
    }

    public boolean hasData() {
        return hasData;
    }

    @SuppressWarnings("unchecked")
    public void injectData(T t) {
        ArrayList<SharedFlow> copy = null;
        synchronized (FlowPublisher.this) {
            data = t;
            hasData = true;

            if (downStreamList.size() > 0) {
                copy = (ArrayList<SharedFlow>) downStreamList.clone();
            }
        }
        if (copy != null) {
            for (int i = 0; i < copy.size(); i++) {
                copy.get(i).consumer.accept(t);
            }
        }
    }

    private class PublishConsumer implements FlowConsumer<T> {

        @Override
        public void accept(T t) {
            FlowPublisher.this.injectData(t);
        }

        @Override
        public void onComplete() {
            ArrayList<SharedFlow> copy = null;
            synchronized (FlowPublisher.this) {
                if (downStreamList.size() > 0) {
                    copy = (ArrayList<SharedFlow>) downStreamList.clone();
                }
            }
            if (copy != null) {
                for (int i = 0; i < copy.size(); i++) {
                    copy.get(i).consumer.onComplete();
                }
            }
        }

        @Override
        public void onCancel() {
            ArrayList<SharedFlow> copy = null;
            synchronized (FlowPublisher.this) {
                if (downStreamList.size() > 0) {
                    copy = (ArrayList<SharedFlow>) downStreamList.clone();
                }
            }
            if (copy != null) {
                for (int i = 0; i < copy.size(); i++) {
                    copy.get(i).consumer.onCancel();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            ArrayList<SharedFlow> copy = null;
            synchronized (FlowPublisher.this) {
                if (downStreamList.size() > 0) {
                    copy = (ArrayList<SharedFlow>) downStreamList.clone();
                }
            }
            if (copy != null) {
                for (int i = 0; i < copy.size(); i++) {
                    copy.get(i).consumer.onError(throwable);
                }
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
