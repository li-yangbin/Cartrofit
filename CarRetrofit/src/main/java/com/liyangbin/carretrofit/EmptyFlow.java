package com.liyangbin.carretrofit;

public interface EmptyFlow extends Flow<Void> {
    void addEmptyObserver(Runnable runnable);
    void removeEmptyObserver(Runnable runnable);
}