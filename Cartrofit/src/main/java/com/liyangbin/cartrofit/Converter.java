package com.liyangbin.cartrofit;

import java.util.function.Function;

public interface Converter<T, R> extends Function<T, R> {
    R convert(T value);

    @Override
    default R apply(T t) {
        return convert(t);
    }
}