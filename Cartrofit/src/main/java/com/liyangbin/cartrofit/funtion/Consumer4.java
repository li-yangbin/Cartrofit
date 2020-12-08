package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.CartrofitGrammarException;

public interface Consumer4<T1, T2, T3, T4> extends FunctionalConsumer {

    @Override
    default void apply(Object[] objects) {
        if (objects.length != 4) {
            throw new CartrofitGrammarException("Input elements size:"
                    + objects.length + " do not match converter:" + this);
        }
        apply((T1) objects[0], (T2) objects[1], (T3) objects[2], (T4) objects[3]);
    }

    void apply(T1 t1, T2 t2, T3 t3, T4 t4);
}
