package com.liyangbin.cartrofit.solution;

public interface Accumulator<V, R> extends AbsAccumulator<ParaVal[], R> {
    @Override
    default R advance(R old, ParaVal[] para) {
        return advance(old, (ParaVal<V>) para[0]);
    }

    R advance(R old, ParaVal<V> para);
}