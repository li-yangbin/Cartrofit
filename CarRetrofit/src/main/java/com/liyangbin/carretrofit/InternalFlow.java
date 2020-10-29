package com.liyangbin.carretrofit;

import java.util.function.Consumer;

interface InternalFlow extends Flow<Object> {
    default void addObserver2(ThrowableConsumer consumer) {
    }
    void removeObserver2(ThrowableConsumer consumer);
}
