package com.liyangbin.cartrofit.funtion;

public interface Consumer2<T1, T2> extends Consumer<Union.Union2<T1, T2>> {

    @Override
    default void accept(Union.Union2<T1, T2> union) {
        apply(union.value1, union.value2);
    }

    void accept(T1 t1, T2 t2);
}
