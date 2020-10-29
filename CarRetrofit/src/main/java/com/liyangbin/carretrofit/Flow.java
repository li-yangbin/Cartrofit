package com.liyangbin.carretrofit;

import java.util.function.Consumer;

public interface Flow<T> {
    void addObserver(ThrowableConsumer<T> consumer);
    void removeObserver(ThrowableConsumer<T> consumer);
}
