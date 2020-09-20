package com.liyangbin.carretrofit.funtion;

public interface Function3<T1, T2, T3, R> extends Operator {
    default R apply(int effectIndex, T1 t1, T2 t2, T3 t3) {
        return apply(t1, t2, t3);
    }

    R apply(T1 t1, T2 t2, T3 t3);
}
