package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.CartrofitGrammarException;

public interface Consumer2<T1, T2> extends FunctionalConsumer {

    @Override
    default void apply(Object[] objects) {
        if (objects.length != 2) {
            throw new CartrofitGrammarException("Input elements size:"
                    + objects.length + " do not match converter:" + this);
        }
        apply((T1) objects[0], (T2) objects[1]);
    }

    void apply(T1 t1, T2 t2);
}
