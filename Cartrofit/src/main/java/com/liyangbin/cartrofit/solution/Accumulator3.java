package com.liyangbin.cartrofit.solution;

import java.lang.annotation.Annotation;

public interface Accumulator3<A extends Annotation, V1, V2, V3, R> extends AbsAccumulator<A, ParaVal[], R> {
    @Override
    default R advance(A annotation, R old, ParaVal[] para) {
        return advance(annotation, old, (ParaVal<V1>) para[0], (ParaVal<V2>) para[1], (ParaVal<V3>) para[2]);
    }

    R advance(A annotation, R old, ParaVal<V1> para1, ParaVal<V2> para2, ParaVal<V3> para3);
}