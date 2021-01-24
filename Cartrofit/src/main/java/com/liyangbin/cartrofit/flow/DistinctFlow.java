package com.liyangbin.cartrofit.flow;

import java.util.function.BiPredicate;

public class DistinctFlow<T> extends Flow<T> {

    private final Flow<T> upStream;
    private final BiPredicate<T, T> distinctCheck;

    public DistinctFlow(Flow<T> upStream, BiPredicate<T, T> distinctCheck) {
        this.upStream = upStream;
        this.distinctCheck = distinctCheck;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(new InnerConsumer(consumer));
    }

    @Override
    protected void onSubscribeStopped() {
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class InnerConsumer implements FlowConsumer<T> {

        FlowConsumer<T> downStream;
        T lastValue;

        InnerConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            if (distinctCheck.test(lastValue, t)) {
                lastValue = t;
                downStream.accept(t);
            }
        }

        @Override
        public void onComplete() {
            downStream.onComplete();
        }
    }
}
