package com.liyangbin.cartrofit.solution;

public interface Accumulator5<V1, V2, V3, V4, V5, R> extends AbsAccumulator<ParaVal[], R> {
    @Override
    default R advance(R old, ParaVal[] para) {
        return advance(old, (ParaVal<V1>) para[0], (ParaVal<V2>) para[1], (ParaVal<V3>) para[2],
                (ParaVal<V4>) para[3], (ParaVal<V5>) para[4]);
    }

    R advance(R old, ParaVal<V1> more1, ParaVal<V2> more2, ParaVal<V3> more3, ParaVal<V4> more4,
              ParaVal<V5> more5);
}