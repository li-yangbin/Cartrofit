package com.liyangbin.carretrofit;

import java.util.function.Consumer;

public interface Flow<T> {
    void addObserver(Consumer<T> consumer);
    void removeObserver(Consumer<T> consumer);
}
