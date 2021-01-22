package com.liyangbin.cartrofit.flow;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;
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
        ArrayList<Flow<T>> mappedFlowList = new ArrayList<>();
        boolean expired;

        InnerConsumer(Consumer<T> downStream) {
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
            flow.subscribe(new FlatConsumer());
        }

        void stopSubscribe() {
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

        private class FlatConsumer implements Consumer<T> {

            @Override
            public void accept(T t) {
                downStream.accept(t);
            }
        }
    }
}
