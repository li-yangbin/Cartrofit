package com.liyangbin.cartrofit.flow;

import java.util.Objects;
import java.util.function.Function;

public class SwitchMapFlow<T, U> extends Flow<T> {
    private final Flow<U> upStream;
    private final Function<U, Flow<T>> flatMapper;

    public SwitchMapFlow(Flow<U> upStream, Function<U, Flow<T>> flatMapper) {
        this.upStream = upStream;
        this.flatMapper = flatMapper;
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

    private class InnerConsumer implements FlowConsumer<U> {
        Flow<T> lastMappedFlow;
        FlowConsumer<T> downStream;
        boolean upStreamCompleted;
        boolean expired;

        InnerConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(U t) {
            Flow<T> lastFlow;
            Flow<T> newestMappedFlow = Objects.requireNonNull(flatMapper.apply(t));
            synchronized (this) {
                if (expired || upStreamCompleted) {
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

        @Override
        public void onCancel() {
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

        @Override
        public void onComplete() {
            boolean doCallDownStreamComplete;
            synchronized (this) {
                upStreamCompleted = true;
                doCallDownStreamComplete = evaluateDownStreamCompleteLocked();
            }
            if (doCallDownStreamComplete) {
                downStream.onComplete();
            }
        }

        boolean evaluateDownStreamCompleteLocked() {
            if (!expired && upStreamCompleted) {
                return lastMappedFlow == null
                        || lastMappedFlow.isSubscribeStopped();
            }
            return false;
        }

        private class FlatConsumer implements FlowConsumer<T> {

            @Override
            public void accept(T t) {
                if (expired) {
                    return;
                }
                downStream.accept(t);
            }

            @Override
            public void onCancel() {
                boolean doCallDownStreamComplete;
                synchronized (InnerConsumer.this) {
                    lastMappedFlow = null;
                    doCallDownStreamComplete = evaluateDownStreamCompleteLocked();
                }
                if (doCallDownStreamComplete) {
                    downStream.onComplete();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (InnerConsumer.this) {
                    lastMappedFlow = null;
                    expired = true;
                }
                upStream.stopSubscribe();
                downStream.onError(throwable);
            }

            @Override
            public void onComplete() {
                boolean doCallDownStreamComplete;
                synchronized (InnerConsumer.this) {
                    lastMappedFlow = null;
                    doCallDownStreamComplete = evaluateDownStreamCompleteLocked();
                }
                if (doCallDownStreamComplete) {
                    downStream.onComplete();
                }
            }
        }
    }
}
