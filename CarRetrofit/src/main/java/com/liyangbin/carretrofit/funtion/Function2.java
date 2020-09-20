package com.liyangbin.carretrofit.funtion;

public interface Function2<T1, T2, R> extends Operator {
    default R apply(int effectIndex, T1 t1, T2 t2) {
        return apply(t1, t2);
    }

    R apply(T1 t1, T2 t2);
}
