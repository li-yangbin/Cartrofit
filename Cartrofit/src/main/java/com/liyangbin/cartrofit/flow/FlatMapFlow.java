package com.liyangbin.cartrofit.flow;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

public class FlatMapFlow<T, U> extends Flow<T> {
    private final Flow<U> upStream;
    private final Function<U, Flow<T>> flatMapper;
    private InnerConsumer upStreamConsumer;

    public FlatMapFlow(Flow<U> upStream, Function<U, Flow<T>> flatMapper) {
        this.upStream = upStream;
        this.flatMapper = flatMapper;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(upStreamConsumer = new InnerConsumer(consumer));
    }

    @Override
    protected void onSubscribeStopped() {
        upStreamConsumer.expire();
        upStreamConsumer = null;
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
                if (expired) {
                    return;
                }
                flow = Objects.requireNonNull(flatMapper.apply(t));
                mappedFlowList.add(flow);
            }
            flow.subscribe(new FlatConsumer(flow));
        }

        void expire() {
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
                if (!expired && !upStreamCompleted) {
                    upStreamCompleted = true;
                    doCallDownStreamComplete = mappedFlowList.isEmpty();
                }
            }
            if (doCallDownStreamComplete) {
                downStream.onComplete();
            }
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
            public void onComplete() {
                boolean doCallDownStreamComplete = false;
                synchronized (InnerConsumer.this) {
                    if (!expired && mappedFlowList.remove(targetFlow)
                            && upStreamCompleted
                            && mappedFlowList.isEmpty()) {
                        doCallDownStreamComplete = true;
                    }
                }
                if (doCallDownStreamComplete) {
                    downStream.onComplete();
                }
            }
        }
    }
}
