package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.CartrofitGrammarException;

public interface Consumer3<T1, T2, T3> extends FunctionalConsumer {

    @Override
    default void apply(Object[] objects) {
        if (objects.length != 3) {
            throw new CartrofitGrammarException("Input elements size:"
                    + objects.length + " do not match converter:" + this);
        }
        apply((T1) objects[0], (T2) objects[1], (T3) objects[2]);
    }

    void apply(T1 t1, T2 t2, T3 t3);
}
