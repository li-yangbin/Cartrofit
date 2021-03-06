package com.liyangbin.cartrofit.solution;

import java.lang.annotation.Annotation;

public interface Accumulator5<A extends Annotation, V1, V2, V3, V4, V5, R> extends AbsAccumulator<A, ParaVal[], R> {
    @Override
    default R advance(A annotation, R old, ParaVal[] para) {
        return advance(annotation, old, (ParaVal<V1>) para[0], (ParaVal<V2>) para[1],
                (ParaVal<V3>) para[2], (ParaVal<V4>) para[3], (ParaVal<V5>) para[4]);
    }

    R advance(A annotation, R old, ParaVal<V1> para1, ParaVal<V2> para2, ParaVal<V3> para3, ParaVal<V4> para4,
              ParaVal<V5> para5);
}