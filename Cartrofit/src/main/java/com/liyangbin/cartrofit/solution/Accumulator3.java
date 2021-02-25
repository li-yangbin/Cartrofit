package com.liyangbin.cartrofit.solution;

public interface Accumulator3<V1, V2, V3, R> extends AbsAccumulator<ParaVal[], R> {
    @Override
    default R advance(R old, ParaVal[] para) {
        return advance(old, (ParaVal<V1>) para[0], (ParaVal<V2>) para[1], (ParaVal<V3>) para[2]);
    }

    R advance(R old, ParaVal<V1> more1, ParaVal<V2> more2, ParaVal<V3> more3);
}