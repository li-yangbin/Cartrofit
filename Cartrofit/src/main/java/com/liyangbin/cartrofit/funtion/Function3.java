package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.Converter;

public interface Function3<T1, T2, T3, R> extends Converter<Object[], R>, FunctionalCombinator<R> {
    default R apply(int effectIndex, T1 t1, T2 t2, T3 t3) {
        return apply(t1, t2, t3);
    }

    default R apply(int effectIndex, Object[] objects) {
        if (objects.length != 3) {
            throw new Cartrofit.CartrofitException("Input elements size:"
                    + objects.length + " do not match converter:" + this);
        }
        return apply(effectIndex, (T1) objects[0], (T2) objects[1], (T3) objects[2]);
    }

    default R convert(Object[] objects) {
        return apply(-1, objects);
    }

    R apply(T1 t1, T2 t2, T3 t3);
}
