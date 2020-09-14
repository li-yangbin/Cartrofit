package com.liyangbin.carretrofit.funtion;

public interface Function4<T1, T2, T3, T4, R> extends Operator {
    R apply(T1 t1, T2 t2, T3 t3, T4 t4);
}
