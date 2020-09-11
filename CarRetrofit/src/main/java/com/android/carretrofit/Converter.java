package com.android.carretrofit;

import java.util.function.Function;

public interface Converter<T, R> extends Function<T, R> {
    R convert(T value);

    @Override
    default R apply(T t) {
        return convert(t);
    }
}

interface ConverterMapper<DATA, T> {
    <NEW_R> Object map(T t, Converter<DATA, NEW_R> converter);
}