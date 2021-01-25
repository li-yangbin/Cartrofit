package com.liyangbin.cartrofit.flow;


import java.util.function.BiPredicate;

public class DistinctFlow<T> extends Flow.WrappedFlow<T> {

    private final BiPredicate<T, T> distinctCheck;

    public DistinctFlow(Flow<T> upStream, BiPredicate<T, T> distinctCheck) {
        super(upStream);
        this.distinctCheck = distinctCheck;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(new InnerConsumer(consumer));
    }

    private class InnerConsumer extends WrappedFusedConsumer<T, T> {

        T lastValue;

        InnerConsumer(FlowConsumer<T> downStream) {
            super(downStream);
        }

        @Override
        public void accept(T t) {
            if (!done && distinctCheck.test(lastValue, t)) {
                lastValue = t;
                downStream.accept(t);
            }
        }
    }
}
