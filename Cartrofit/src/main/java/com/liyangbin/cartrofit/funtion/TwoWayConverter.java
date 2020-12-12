package com.liyangbin.cartrofit.funtion;

public interface TwoWayConverter<T, R> extends Converter<T, R> {

    T reverse(R r);

    default Converter<R, T> reverseConverter() {
        return this::reverse;
    }
}
