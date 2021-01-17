package com.liyangbin.cartrofit;

import java.util.ArrayList;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class FlowWrapper implements Flow<Object>, Consumer<Object> {

    ArrayList<Call.OnReceiveCall> receiverList = new ArrayList<>();
    private ArrayList<Consumer<Object>> consumerList = new ArrayList<>();
    private Flow<Object> base;

    public FlowWrapper(Flow<?> base) {
        this.base = (Flow<Object>) base;
    }

    FlowWrapper(Flow<?> base, Call.OnReceiveCall onReceiveCall) {
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
            // TODO: intercept after advance
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

    FlowWrapper addReceiverCall(Call.OnReceiveCall receiver) {
        synchronized (this) {
            receiverList.add(receiver);
        }
        return this;
    }

    void onReceiveComplete(Call.OnReceiveCall receiver, Object obj) {
        Call.OnReceiveCall nextReceiver = null;
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
