package com.liyangbin.carretrofit.funtion;

import com.liyangbin.carretrofit.Converter;

public interface Function6<T1, T2, T3, T4, T5, T6, R> extends Converter<Object[], R> {
    default R apply(int effectIndex, T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
        return apply(t1, t2, t3, t4, t5, t6);
    }

    default R convert(Object[] objects) {
        return apply((T1) objects[0], (T2) objects[1], (T3) objects[2], (T4) objects[3],
                (T5) objects[4], (T6) objects[5]);
    }

    R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6);
}
