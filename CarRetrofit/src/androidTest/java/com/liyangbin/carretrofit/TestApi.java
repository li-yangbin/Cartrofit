package com.liyangbin.carretrofit;

public interface TestApi {
    default void test() {
        System.out.println("test static this:" + this);
    }

    default void test(String parameter) {
        System.out.println("test static this:" + this + " parameter:" + parameter);
    }
}
