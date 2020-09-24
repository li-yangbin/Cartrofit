package com.liyangbin.carretrofit.funtion;

import com.liyangbin.carretrofit.Converter;

public interface Function2<T1, T2, R> extends Converter<Object[], R> {
    default R apply(int effectIndex, T1 t1, T2 t2) {
        return apply(t1, t2);
    }

    default R convert(Object[] objects) {
        return apply((T1) objects[0], (T2) objects[1]);
    }

    R apply(T1 t1, T2 t2);
}
