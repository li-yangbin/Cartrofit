package com.liyangbin.cartrofit.funtion;

public interface TwoWayConverter2<T1, T2, R> extends TwoWayConverter<Union.Union2<T1, T2>, R> {

    @Override
    default R convert(Union.Union2<T1, T2> union) {
        return convert(union.value1, union.value2);
    }

    R convert(T1 t1, T2 t2);
}
