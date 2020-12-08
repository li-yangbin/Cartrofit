package com.liyangbin.cartrofit;

public interface ApiCallback {
    void onApiCreate(Class<?> apiClass, CommandBuilder builder);
}
