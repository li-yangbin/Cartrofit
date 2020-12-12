package com.liyangbin.cartrofit.funtion;

public interface Converter<T, R> {
    R convert(T value);
}