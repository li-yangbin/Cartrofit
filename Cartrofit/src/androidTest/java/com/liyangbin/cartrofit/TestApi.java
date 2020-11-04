package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.CarApi;

@CarApi
public interface TestApi {
    default void test() {
        System.out.println("test static this:" + this);
    }

    default void test(String parameter) {
        System.out.println("test static this:" + this + " parameter:" + parameter);
    }
}
