package com.liyangbin.cartrofit.flow;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TakeFlow<T> extends Flow<T> {
    private final Flow<T> upStream;
    private final Predicate<T> takeCheck;
    private final int keepCountChecked;

    public TakeFlow(Flow<T> upStream, Predicate<T> takeCheck, int keepCountChecked) {
        this.upStream = upStream;
        this.takeCheck = takeCheck;
        this.keepCountChecked = keepCountChecked;
    }

    @Override
    protected void onSubscribeStarted(Consumer<T> consumer) {
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

    private class InnerConsumer implements Consumer<T> {
        Consumer<T> downStream;
        int index;

        InnerConsumer(Consumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            if (takeCheck == null || takeCheck.test(t)) {
                boolean doUnSubscribe = false;
                boolean doAccept = true;

                if (keepCountChecked > 0) {
                    synchronized (this) {
                        index++;
                        if (index == keepCountChecked) {
                            doUnSubscribe = true;
                        } else if (index > keepCountChecked) {
                            doAccept = false;
                        }
                    }
                }

                if (doAccept) {
                    downStream.accept(t);
                }

                if (doUnSubscribe) {
                    stopSubscribe();
                }
            }
        }
    }
}
