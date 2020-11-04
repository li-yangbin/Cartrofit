package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.Converter;

public interface FunctionalCombinator<R> extends Converter<Object[], R> {
    @Override
    default R convert(Object[] objects) {
        return apply(-1, objects);
    }

    R apply(int effectIndex, Object[] objects);
}
