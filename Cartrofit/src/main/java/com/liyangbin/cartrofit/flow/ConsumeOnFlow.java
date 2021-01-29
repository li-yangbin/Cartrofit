package com.liyangbin.cartrofit.flow;

import java.util.concurrent.Executor;

public class ConsumeOnFlow<T> extends Flow.WrappedFlow<T> {
    private static final Object NOT_SET = new Object();
    private final Executor handler;

    ConsumeOnFlow(Flow<T> upStream, Executor handler) {
        super(upStream);
        this.handler = handler;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(new InnerConsumer(consumer));
    }

    private class InnerConsumer extends WrappedFusedConsumer<T, T> implements Runnable {

        Object mPendingData = NOT_SET;

        InnerConsumer(FlowConsumer<T> downStream) {
            super(downStream);
        }

        @Override
        public void accept(T t) {
            if (done) return;

            boolean postTask;
            synchronized (this) {
                postTask = mPendingData == NOT_SET;
                mPendingData = t;
            }
            if (postTask) {
                handler.execute(this);
            }
        }

        @Override
        public void run() {
            if (done) return;

            T newValue;
            synchronized (this) {
                newValue = (T) mPendingData;
                mPendingData = NOT_SET;
            }
            downStream.accept(newValue);
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                handler.execute(() -> downStream.onComplete());
            }
        }

//        @Override
//        public void onError(Throwable throwable) {
//            if (!done) {
//                done = true;
//                handler.execute(() -> downStream.onError(throwable));
//            }
//        }
    }
}
