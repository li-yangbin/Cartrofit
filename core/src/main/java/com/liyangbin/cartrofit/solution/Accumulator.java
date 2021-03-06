package com.liyangbin.cartrofit.solution;

import java.lang.annotation.Annotation;

public interface Accumulator<A extends Annotation, V, R> extends AbsAccumulator<A, ParaVal[], R> {
    @Override
    default R advance(A annotation, R old, ParaVal[] para) {
        return advance(annotation, old, (ParaVal<V>) para[0]);
    }

    R advance(A annotation, R old, ParaVal<V> para);
}