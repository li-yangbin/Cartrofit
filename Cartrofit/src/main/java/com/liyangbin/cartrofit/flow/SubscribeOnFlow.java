package com.liyangbin.cartrofit.flow;

import java.util.concurrent.Executor;

public class SubscribeOnFlow<T> extends Flow.WrappedFlow<T> {
    private final Executor handler;

    SubscribeOnFlow(Flow<T> upStream, Executor handler) {
        super(upStream);
        this.handler = handler;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        handler.execute(() -> upStream.subscribe(new InnerConsumer(consumer)));
    }

    @Override
    protected void onSubscribeStopped() {
        handler.execute(() -> upStream.stopSubscribe());
    }

    private class InnerConsumer extends WrappedFusedConsumer<T, T> {

        InnerConsumer(FlowConsumer<T> downStream) {
            super(downStream);
        }

        @Override
        public void accept(T t) {
            if (!done) {
                downStream.accept(t);
            }
        }
    }
}
