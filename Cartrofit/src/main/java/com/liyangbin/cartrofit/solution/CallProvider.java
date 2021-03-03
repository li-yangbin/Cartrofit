package com.liyangbin.cartrofit.solution;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Key;

import java.lang.annotation.Annotation;

public interface CallProvider<A extends Annotation, T extends Call> {
    T provide(A annotation, Key key);
}
