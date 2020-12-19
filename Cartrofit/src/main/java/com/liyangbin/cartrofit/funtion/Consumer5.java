package com.liyangbin.cartrofit.funtion;

import com.liyangbin.cartrofit.CartrofitGrammarException;

public interface Consumer5<T1, T2, T3, T4, T5> extends Consumer<Object[]> {

    @Override
    default void accept(Object[] objects) {
        if (objects.length != 5) {
            throw new CartrofitGrammarException("Input elements size:"
                    + objects.length + " do not match converter:" + this);
        }
        accept((T1) objects[0], (T2) objects[1], (T3) objects[2], (T4) objects[3], (T5) objects[4]);
    }

    void accept(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5);
}
