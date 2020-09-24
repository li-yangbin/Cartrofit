package com.liyangbin.carretrofit.funtion;

import com.liyangbin.carretrofit.Converter;

public interface Function4<T1, T2, T3, T4, R> extends Converter<Object[], R> {
    default R apply(int effectIndex, T1 t1, T2 t2, T3 t3, T4 t4) {
        return apply(t1, t2, t3, t4);
    }

    default R convert(Object[] objects) {
        return apply((T1) objects[0], (T2) objects[1], (T3) objects[2], (T4) objects[3]);
    }

    R apply(T1 t1, T2 t2, T3 t3, T4 t4);
}
