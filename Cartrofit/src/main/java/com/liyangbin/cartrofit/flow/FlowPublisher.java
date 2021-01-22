package com.liyangbin.cartrofit.flow;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

public class FlowPublisher<T> {

    private final Flow<T> upStream;
    private volatile T data;
    private boolean hasData;
    private boolean active;
    private boolean startWhenConnected;
    private final ArrayList<SharedFlow> downStreamList = new ArrayList<>();
    private final HashMap<Consumer<T>, Flow<T>> listenerMap = new HashMap<>();

    public FlowPublisher(Flow<T> upStream) {
        this.upStream = upStream;
    }

    public void start() {
        upStream.subscribe(new PublishConsumer());
        active = true;
    }

    public void startWhenConnected() {
        startWhenConnected = true;
    }

    public void stop() {
        upStream.stopSubscribe();
        active = false;
    }

    public void addSubscriber(Consumer<T> consumer) {
        if (listenerMap.containsKey(consumer)) {
            return;
        }
        Flow<T> sharedFlow = share();
        listenerMap.put(consumer, sharedFlow);
        sharedFlow.subscribe(consumer);
    }

    public void removeSubscriber(Consumer<T> consumer) {
        Flow<T> sharedFlow = listenerMap.remove(consumer);
        if (sharedFlow != null) {
            sharedFlow.stopSubscribe();
        }
    }

    void onClientSubscribed(SharedFlow flow) {
        boolean doSubscribe = false;
        synchronized (this) {
            boolean oldActive = active;
            downStreamList.add(flow);
            if (!active && downStreamList.size() == 1) {
                active = true;
            }
            if (startWhenConnected && !oldActive && active) {
                doSubscribe = true;
            }
        }
        if (doSubscribe) {
            upStream.subscribe(new PublishConsumer());
        }
    }

    synchronized void onClientSubscribeStopped(SharedFlow flow) {
        downStreamList.remove(flow);
        // TODO: unSubscribe upstream in certain condition
    }

    public Flow<T> share() {
        return new SharedFlow();
    }

    public void cleanup() {
        listenerMap.clear();
        final boolean oldActive;
        synchronized (this) {
            oldActive = active;
            if (active) {
                active = false;
            }
        }
        if (oldActive) {
            upStream.stopSubscribe();
        }
    }

    public final LiveData<T> toLiveData() {
        return share().toLiveData();
    }

    public T getData() {
        return data;
    }

    public boolean hasData() {
        return hasData;
    }

    @SuppressWarnings("unchecked")
    public void injectData(T t) {
        data = t;
        hasData = true;
        ArrayList<SharedFlow> copy = null;
        synchronized (FlowPublisher.this) {
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

    private class PublishConsumer implements Consumer<T> {

        @Override
        public void accept(T t) {
            FlowPublisher.this.injectData(t);
        }
    }

    private class SharedFlow extends Flow<T> {

        Consumer<T> consumer;

        @Override
        protected void onSubscribeStarted(Consumer<T> consumer) {
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

        @Override
        public LiveData<T> toLiveData() {
            MutableLiveData<T> liveData = (MutableLiveData<T>) super.toLiveData();
            liveData.setValue(data);
            return liveData;
        }
    }
}
