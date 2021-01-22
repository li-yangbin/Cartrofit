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
    protected void onSubscribeStarted(Consumer<R> consumer) {
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

    private class MappedConsumer implements Consumer<T> {
        Consumer<R> downStream;

        MappedConsumer(Consumer<R> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            downStream.accept(converter.convert(t));
        }
    }
}
