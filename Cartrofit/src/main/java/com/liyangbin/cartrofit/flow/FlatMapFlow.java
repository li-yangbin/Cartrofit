package com.liyangbin.cartrofit.flow;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

public class FlatMapFlow<T, U> extends Flow<T> {
    private final Flow<U> upStream;
    private final Function<U, Flow<T>> flatMapper;

    public FlatMapFlow(Flow<U> upStream, Function<U, Flow<T>> flatMapper) {
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
        FlowConsumer<T> downStream;
        ArrayList<Flow<T>> mappedFlowList = new ArrayList<>();
        boolean expired;
        boolean upStreamCompleted;

        InnerConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(U t) {
            Flow<T> flow;
            synchronized (this) {
                if (expired || upStreamCompleted) {
                    return;
                }
                flow = Objects.requireNonNull(flatMapper.apply(t));
                mappedFlowList.add(flow);
            }
            flow.subscribe(new FlatConsumer(flow));
        }

        @Override
        public void onCancel() {
            ArrayList<Flow<T>> mappedFlowListCopy;
            synchronized (this) {
                if (mappedFlowList.size() > 0) {
                    mappedFlowListCopy = (ArrayList<Flow<T>>) mappedFlowList.clone();
                    mappedFlowList.clear();
                } else {
                    mappedFlowListCopy = null;
                }
                expired = true;
            }
            if (mappedFlowListCopy != null) {
                for (int i = 0; i < mappedFlowListCopy.size(); i++) {
                    mappedFlowListCopy.get(i).stopSubscribe();
                }
            }
        }

        @Override
        public void onComplete() {
            boolean doCallDownStreamComplete = false;
            synchronized (this) {
                if (!upStreamCompleted) {
                    upStreamCompleted = true;
                    doCallDownStreamComplete = evaluateDownStreamCompleteLocked();
                }
            }
            if (doCallDownStreamComplete) {
                downStream.onComplete();
            }
        }

        boolean evaluateDownStreamCompleteLocked() {
            if (!expired && upStreamCompleted) {
                for (int i = 0; i < mappedFlowList.size(); i++) {
                    if (!mappedFlowList.get(i).isSubscribeStopped()) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private class FlatConsumer implements FlowConsumer<T> {

            Flow<T> targetFlow;

            FlatConsumer(Flow<T> targetFlow) {
                this.targetFlow = targetFlow;
            }

            @Override
            public void accept(T t) {
                synchronized (this) {
                    if (expired) {
                        return;
                    }
                }
                downStream.accept(t);
            }

            @Override
            public void onCancel() {
                boolean doCallDownStreamComplete = false;
                synchronized (InnerConsumer.this) {
                    if (!expired && mappedFlowList.remove(targetFlow)) {
                        doCallDownStreamComplete = evaluateDownStreamCompleteLocked();
                    }
                }
                if (doCallDownStreamComplete) {
                    downStream.onComplete();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (InnerConsumer.this) {
                    mappedFlowList.remove(targetFlow);
                    expired = true;
                }
                upStream.stopSubscribe();
                downStream.onError(throwable);
            }

            @Override
            public void onComplete() {
                boolean doCallDownStreamComplete = false;
                synchronized (InnerConsumer.this) {
                    if (!expired && mappedFlowList.remove(targetFlow)) {
                        doCallDownStreamComplete = evaluateDownStreamCompleteLocked();
                    }
                }
                if (doCallDownStreamComplete) {
                    downStream.onComplete();
                }
            }
        }
    }
}
