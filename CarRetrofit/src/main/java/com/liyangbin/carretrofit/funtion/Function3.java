package com.liyangbin.carretrofit.funtion;

import com.liyangbin.carretrofit.Converter;

public interface Function3<T1, T2, T3, R> extends Converter<Object[], R> {
    default R apply(int effectIndex, T1 t1, T2 t2, T3 t3) {
        return apply(t1, t2, t3);
    }

    default R convert(Object[] objects) {
        return apply((T1) objects[0], (T2) objects[1], (T3) objects[2]);
    }

    R apply(T1 t1, T2 t2, T3 t3);
}
