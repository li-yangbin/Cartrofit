package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Flow;

import java.util.ArrayList;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class FlowWrapper implements Flow<Object>, Consumer<Object> {

    ArrayList<OnReceiveCall> receiverList = new ArrayList<>();
    ArrayList<Consumer<Object>> consumerList = new ArrayList<>();
    Flow<Object> base;

    FlowWrapper(Flow<?> base) {
        this.base = (Flow<Object>) base;
    }

    public FlowWrapper(Flow<?> base, OnReceiveCall onReceiveCall) {
        this.base = (Flow<Object>) base;
        this.receiverList.add(onReceiveCall);
    }

    @Override
    public void addObserver(Consumer<Object> consumer) {
        consumerList.add(consumer);
        if (consumerList.size() == 1) {
            base.addObserver(this);
        }
    }

    @Override
    public void removeObserver(Consumer<Object> consumer) {
        if (consumerList.remove(consumer) && consumerList.size() == 0) {
            base.removeObserver(this);
        }
    }

    @Override
    public void accept(Object value) {
        if (receiverList.size() == 0) {
            dispatchResult(value);
        } else {
            receiverList.get(0).invokeWithFlow(this, value);
        }
    }

    private void dispatchResult(Object value) {
        synchronized (this) {
            for (int i = 0; i < consumerList.size(); i++) {
                consumerList.get(i).accept(value);
            }
        }
    }

    public FlowWrapper addReceiverCall(OnReceiveCall receiver) {
        synchronized (this) {
            receiverList.add(receiver);
        }
        return this;
    }

    void onReceiveComplete(OnReceiveCall receiver, Object obj) {
        OnReceiveCall nextReceiver = null;
        synchronized (this) {
            for (int i = 0; i < receiverList.size() - 1; i++) {
                if (receiverList.get(i) == receiver) {
                    nextReceiver = receiverList.get(i + 1);
                    break;
                }
            }
        }
        if (nextReceiver != null) {
            nextReceiver.invokeWithFlow(this, obj);
            return;
        }
        dispatchResult(obj);
    }
}
