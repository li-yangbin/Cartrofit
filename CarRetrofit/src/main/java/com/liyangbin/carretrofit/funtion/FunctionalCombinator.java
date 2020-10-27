package com.liyangbin.carretrofit.funtion;

import com.liyangbin.carretrofit.Converter;

public interface FunctionalCombinator<R> extends Converter<Object[], R> {
    @Override
    default R convert(Object[] objects) {
        return apply(-1, objects);
    }

    R apply(int effectIndex, Object[] objects);
}
