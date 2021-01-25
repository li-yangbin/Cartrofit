package com.liyangbin.cartrofit.flow;

import com.liyangbin.cartrofit.funtion.Converter;

public class MappedFlow<T, R> extends Flow<R> {

    private final Flow<T> upStream;
    private final Converter<T, R> converter;

    public MappedFlow(Flow<T> upStream, Converter<T, R> converter) {
        this.upStream = upStream;
        this.converter = converter;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<R> consumer) {
        upStream.subscribe(new MappedConsumer(consumer));
    }

    @Override
    protected void onSubscribeStopped() {
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class MappedConsumer extends WrappedFusedConsumer<T, R> {

        MappedConsumer(FlowConsumer<R> downStream) {
            super(downStream);
        }

        @Override
        public void accept(T t) {
            if (done) return;
            downStream.accept(converter.convert(t));
        }
    }
}
