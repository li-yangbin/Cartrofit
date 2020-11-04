package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.Converter;

public interface Function2<T1, T2, R> extends FunctionalCombinator<R> {
    @Override
    default R apply(int effectIndex, Object[] objects) {
        if (objects.length != 2) {
            throw new Cartrofit.CartrofitException("Input elements size:"
                    + objects.length + " do not match converter:" + this);
        }
        return apply(effectIndex, (T1) objects[0], (T2) objects[1]);
    }

    default R apply(int effectIndex, T1 t1, T2 t2) {
        return apply(t1, t2);
    }

    R apply(T1 t1, T2 t2);
}
