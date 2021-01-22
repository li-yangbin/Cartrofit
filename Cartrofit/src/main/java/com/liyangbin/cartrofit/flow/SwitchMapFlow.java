package com.liyangbin.cartrofit.flow;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SwitchMapFlow<T, U> extends Flow<T> {
    private final Flow<U> upStream;
    private final Function<U, Flow<T>> flatMapper;
    private InnerConsumer upStreamConsumer;

    public SwitchMapFlow(Flow<U> upStream, Function<U, Flow<T>> flatMapper) {
        this.upStream = upStream;
        this.flatMapper = flatMapper;
    }

    @Override
    protected void onSubscribeStarted(Consumer<T> consumer) {
        upStream.subscribe(upStreamConsumer = new InnerConsumer(consumer));
    }

    @Override
    protected void onSubscribeStopped() {
        upStreamConsumer.stopSubscribe();
        upStreamConsumer = null;
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class InnerConsumer implements Consumer<U> {
        Consumer<T> downStream;
        Flow<T> lastMappedFlow;
        boolean expired;

        InnerConsumer(Consumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(U t) {
            Flow<T> lastFlow;
            Flow<T> newestMappedFlow = Objects.requireNonNull(flatMapper.apply(t));
            synchronized (this) {
                if (expired) {
                    return;
                }
                lastFlow = lastMappedFlow;
                lastMappedFlow = newestMappedFlow;
            }
            if (lastFlow != null) {
                lastFlow.stopSubscribe();
            }
            newestMappedFlow.subscribe(new FlatConsumer());
        }

        void stopSubscribe() {
            Flow<T> lastFlow;
            synchronized (this) {
                lastFlow = lastMappedFlow;
                lastMappedFlow = null;
                expired = true;
            }
            if (lastFlow != null) {
                lastFlow.stopSubscribe();
            }
        }

        private class FlatConsumer implements Consumer<T> {

            @Override
            public void accept(T t) {
                downStream.accept(t);
            }
        }
    }
}
