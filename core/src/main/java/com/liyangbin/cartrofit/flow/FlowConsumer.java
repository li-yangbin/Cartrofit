package com.liyangbin.cartrofit.flow;

import java.util.function.Consumer;

public interface FlowConsumer<T> extends Consumer<T> {
    default void onComplete() {
        // called by up-stream
    }
    default void onCancel() {
        // called by down-stream
    }
    default void onError(Throwable throwable) {
        // called by up-stream
        defaultThrow(throwable, null);
    }

    static void defaultThrow(Throwable throwable, Class<?> from) {
        if (from == null) {
            throw new RuntimeException("Implement onError method to handle exception", throwable);
        }
        throw new RuntimeException("Crash due to uncaught exception!!" +
                " Consider either using ExceptionHandler for type '" + throwable.getClass().getSimpleName()
                + "' or adding @OnError method in " + from, throwable);
    }
}
