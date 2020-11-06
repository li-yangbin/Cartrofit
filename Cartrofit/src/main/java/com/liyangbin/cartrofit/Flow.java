package com.liyangbin.cartrofit;

import java.util.function.Consumer;

public interface Flow<T> {
    void addObserver(Consumer<T> consumer);
    void removeObserver(Consumer<T> consumer);

    interface StickyFlow<T> extends Flow<T> {
        T get();
    }

    interface EmptyFlow extends Flow<Void> {
        void addEmptyObserver(Runnable runnable);
        void removeEmptyObserver(Runnable runnable);
    }
}
