package com.liyangbin.cartrofit.flow;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class TakeFlow<T> extends Flow<T> {
    private final Flow<T> upStream;
    private final Predicate<T> takeCheck;
    private final int keepCountChecked;
    private InnerConsumer innerConsumer;

    public TakeFlow(Flow<T> upStream, Predicate<T> takeCheck, int keepCountChecked) {
        this.upStream = upStream;
        this.takeCheck = takeCheck;
        this.keepCountChecked = keepCountChecked;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(innerConsumer = new InnerConsumer(consumer));
    }

    @Override
    protected void onSubscribeStopped() {
        innerConsumer.expire();
        innerConsumer = null;
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class InnerConsumer implements FlowConsumer<T> {
        FlowConsumer<T> downStream;
        int index;
        AtomicBoolean expiredRef = new AtomicBoolean();

        InnerConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            if (expiredRef.get()) {
                return;
            }
            if (takeCheck == null || takeCheck.test(t)) {
                boolean doComplete = false;
                boolean doAccept = true;

                if (keepCountChecked > 0) {
                    synchronized (this) {
                        index++;
                        if (index == keepCountChecked) {
                            doComplete = true;
                        } else if (index > keepCountChecked) {
                            doAccept = false;
                        }
                    }
                }

                if (doAccept) {
                    downStream.accept(t);
                }

                if (doComplete) {
                    upStream.stopSubscribe();
                    downStream.onComplete();
                }
            }
        }

        void expire() {
            expiredRef.set(true);
        }

        @Override
        public void onComplete() {
            if (expiredRef.getAndSet(true)) {
                return;
            }
            downStream.onComplete();
        }
    }
}
