package com.liyangbin.cartrofit.solution;

interface AbsAccumulator<V, R> {
    R advance(R old, V para);
}
