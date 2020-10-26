package com.liyangbin.carretrofit.funtion;

import com.liyangbin.carretrofit.Converter;

public interface FunctionalCombinator<R> extends Converter<Object[], R> {
    R apply(int effectIndex, Object[] objects);
}
