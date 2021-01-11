package com.liyangbin.cartrofit.funtion;

public interface Converter3<T1, T2, T3, R> extends Converter<Union3<T1, T2, T3>, R> {
    @Override
    default R convert(Union3<T1, T2, T3> union) {
        return convert(union.value1, union.value2, union.value3);
    }

    @Override
    default int getInputCount() {
        return 3;
    }

    R convert(T1 t1, T2 t2, T3 t3);
}
