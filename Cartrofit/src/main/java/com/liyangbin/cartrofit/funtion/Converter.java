package com.liyangbin.cartrofit.funtion;

import java.util.Objects;

public interface Converter<T, R> {
    R convert(T value);

    default <V> Converter<T, V> andThen(Converter<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.convert(convert(t));
    }
}