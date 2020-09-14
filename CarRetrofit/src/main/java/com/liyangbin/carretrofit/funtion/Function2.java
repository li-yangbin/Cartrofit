package com.liyangbin.carretrofit.funtion;

public interface Function2<T1, T2, R> extends Operator {
    R apply(T1 t1, T2 t2);
}
