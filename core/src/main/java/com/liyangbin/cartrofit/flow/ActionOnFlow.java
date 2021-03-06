package com.liyangbin.cartrofit.flow;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class ActionOnFlow<T> extends Flow.WrappedFlow<T> {
    private final Consumer<T> onEach;
    private final Runnable onComplete;
    private final Predicate<Throwable> onError;

    ActionOnFlow(Flow<T> upStream, Consumer<T> onEach, Runnable onComplete,
                 Predicate<Throwable> onError) {
        super(upStream);
        this.onEach = onEach;
        this.onComplete = onComplete;
        this.onError = onError;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        upStream.subscribe(new InnerConsumer(consumer));
    }

    private class InnerConsumer implements FlowConsumer<T> {

        FlowConsumer<T> downStream;
        boolean done;

        InnerConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            if (!done) {

                if (onEach != null) {
                    onEach.accept(t);
                }

                downStream.accept(t);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;

                if (onComplete != null) {
                    onComplete.run();
                }

                downStream.onComplete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (done) return;
            if (onError == null || !onError.test(throwable)) {
                downStream.onError(throwable);
            }
        }

        @Override
        public void onCancel() {
            done = true;
        }
    }
}
