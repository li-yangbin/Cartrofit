package com.liyangbin.carretrofit.funtion;

public interface Function6<T1, T2, T3, T4, T5, T6, R> extends Operator {
    default R apply(int effectIndex, T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
        return apply(t1, t2, t3, t4, t5, t6);
    }

    R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6);
}
