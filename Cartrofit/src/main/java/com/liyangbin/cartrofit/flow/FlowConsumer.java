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
        if (throwable instanceof RuntimeException) {
            throw ((RuntimeException) throwable);
        } else {
            throw new RuntimeException(throwable);
        }
    }
}
