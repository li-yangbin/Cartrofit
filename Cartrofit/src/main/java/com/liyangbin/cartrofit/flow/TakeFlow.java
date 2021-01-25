package com.liyangbin.cartrofit.flow;

import java.util.function.Predicate;

public class TakeFlow<T> extends Flow.WrappedFlow<T> {
    private final Predicate<T> takeCheck;
    private final int keepCountChecked;

    public TakeFlow(Flow<T> upStream, Predicate<T> takeCheck, int keepCountChecked) {
        super(upStream);
        this.takeCheck = takeCheck;
        this.keepCountChecked = keepCountChecked;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(new InnerConsumer(consumer));
    }

    private class InnerConsumer extends WrappedFusedConsumer<T, T> {
        int index;

        InnerConsumer(FlowConsumer<T> downStream) {
            super(downStream);
        }

        @Override
        public void accept(T t) {
            if (done) return;
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
                    onComplete();
                    upStream.stopSubscribe();
                }
            }
        }
    }
}
