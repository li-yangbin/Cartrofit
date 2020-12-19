package com.liyangbin.cartrofit.funtion;

public interface TwoWayConverter4<T1, T2, T3, T4, R> extends TwoWayConverter<Union4<T1, T2, T3, T4>, R> {

    @Override
    default R convert(Union4<T1, T2, T3, T4> union) {
        return convert(union.value1, union.value2, union.value3, union.value4);
    }

    R convert(T1 t1, T2 t2, T3 t3, T4 t4);
}
