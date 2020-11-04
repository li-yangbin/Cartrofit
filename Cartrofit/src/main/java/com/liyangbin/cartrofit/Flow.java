package com.liyangbin.cartrofit;

import java.util.function.Consumer;

public interface Flow<T> {
    void addObserver(Consumer<T> consumer);
    void removeObserver(Consumer<T> consumer);
}
