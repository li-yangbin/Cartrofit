package com.liyangbin.carretrofit;

public interface ThrowableConsumer<T> {
    void receive(T t) throws Throwable;
}
