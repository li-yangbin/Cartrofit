package com.liyangbin.cartrofit;

import java.util.function.Consumer;
import java.util.function.Function;

public interface Flow<T> {
    void addObserver(Consumer<T> consumer);
    void removeObserver(Consumer<T> consumer);

    static <T, R> Flow<R> map(Flow<T> flow, Function<T, R> function) {
        if (flow instanceof MediatorFlow) {
            ((MediatorFlow<T, T>) flow).addMediator(function);
            return (Flow<R>) flow;
        } else {
            return new MediatorFlow<>(flow, function);
        }
    }

    interface StickyFlow<T> extends Flow<T> {
        T get();
    }

    interface EmptyFlow extends Flow<Void> {
        void addEmptyObserver(Runnable runnable);
        void removeEmptyObserver(Runnable runnable);
    }
}
