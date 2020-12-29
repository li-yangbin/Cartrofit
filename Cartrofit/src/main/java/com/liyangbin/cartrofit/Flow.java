package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.call.FlowWrapper;
import com.liyangbin.cartrofit.call.MediatorFlow;
import com.liyangbin.cartrofit.call.OnReceiveCall;
import com.liyangbin.cartrofit.call.StickyFlowImpl;
import com.liyangbin.cartrofit.funtion.Converter;

import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public interface Flow<T> {
    void addObserver(Consumer<T> consumer);
    void removeObserver(Consumer<T> consumer);

    default <T2> Flow<T2> map(Converter<T, T2> converter) {
        if (this instanceof MediatorFlow) {
            return (Flow<T2>) ((MediatorFlow) this).addMediator(converter);
        } else {
            return (Flow<T2>) new MediatorFlow(this, converter);
        }
    }

    default Flow<T> untilReceive(OnReceiveCall onReceiveCall) {
        if (this instanceof FlowWrapper) {
            return (Flow<T>) ((FlowWrapper) this).addReceiverCall(onReceiveCall);
        } else {
            return (Flow<T>) new FlowWrapper(this, onReceiveCall);
        }
    }

    default StickyFlow<T> sticky() {
        if (this instanceof StickyFlow) {
            return (StickyFlow<T>) this;
        } else {
            return (StickyFlow<T>) new StickyFlowImpl(this);
        }
    }

    interface StickyFlow<T> extends Flow<T> {
        T get();
    }

    // TODO impl
    interface EmptyFlow extends Flow<Void> {
        void addEmptyObserver(Runnable runnable);
        void removeEmptyObserver(Runnable runnable);
    }
}
