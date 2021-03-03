package com.liyangbin.cartrofit.solution;

import java.lang.annotation.Annotation;

public interface Accumulator2<A extends Annotation, V1, V2, R> extends AbsAccumulator<A, ParaVal[], R> {
    @Override
    default R advance(A Annotation, R old, ParaVal[] para) {
        return advance(Annotation, old, (ParaVal<V1>) para[0], (ParaVal<V2>) para[1]);
    }

    R advance(A Annotation, R old, ParaVal<V1> para1, ParaVal<V2> para2);
}