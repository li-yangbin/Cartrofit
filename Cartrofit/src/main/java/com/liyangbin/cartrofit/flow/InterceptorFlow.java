package com.liyangbin.cartrofit.flow;

import java.util.function.Consumer;

public class InterceptorFlow<T> extends Flow<T> {

    private final Flow<T> upStream;
    private final Interceptor<T> interceptor;

    public interface Interceptor<T> {
        void onIntercept(Consumer<T> nextConsumer, T next);
    }

    public InterceptorFlow(Flow<T> upStream, Interceptor<T> interceptor) {
        this.upStream = upStream;
        this.interceptor = interceptor;
    }

    @Override
    protected void onSubscribeStarted(Consumer<T> consumer) {
        upStream.subscribe(new InterceptConsumer(consumer));
    }

    @Override
    protected void onSubscribeStopped() {
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class InterceptConsumer implements Consumer<T> {
        Consumer<T> downStream;
        Consumer<T> nextAction = new Consumer<T>() {
            @Override
            public void accept(T t) {
                downStream.accept(t);
            }
        };

        InterceptConsumer(Consumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            interceptor.onIntercept(nextAction, t);
        }
    }
}
