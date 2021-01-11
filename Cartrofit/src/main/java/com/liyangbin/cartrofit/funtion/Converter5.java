package com.liyangbin.cartrofit.funtion;

public interface Converter5<T1, T2, T3, T4, T5, R> extends Converter<Union5<T1, T2, T3, T4, T5>, R> {
    @Override
    default R convert(Union5<T1, T2, T3, T4, T5> union) {
        return convert(union.value1, union.value2, union.value3, union.value4, union.value5);
    }

    @Override
    default int getInputCount() {
        return 5;
    }

    R convert(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5);
}
