package com.liyangbin.cartrofit.solution;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CartrofitContext;
import com.liyangbin.cartrofit.Key;

import java.lang.annotation.Annotation;

public interface CallProvider2<A extends Annotation, T extends Call> extends CallProvider<A, T> {

    T provide(CartrofitContext context, int category, A annotation, Key key);

    @Override
    default T provide(A annotation, Key key) {
        return provide(null, 0, annotation, key);
    }
}