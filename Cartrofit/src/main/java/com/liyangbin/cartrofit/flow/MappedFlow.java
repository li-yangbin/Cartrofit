package com.liyangbin.cartrofit.flow;

import com.liyangbin.cartrofit.funtion.Converter;

import java.util.function.Consumer;

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

    private class MappedConsumer implements FlowConsumer<T> {
        FlowConsumer<R> downStream;

        MappedConsumer(FlowConsumer<R> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            downStream.accept(converter.convert(t));
        }

        @Override
        public void onComplete() {
            downStream.onComplete();
        }
    }
}
