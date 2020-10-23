package com.liyangbin.carretrofit.funtion;

public interface FunctionalCombinator<T> {
    T apply(int effectIndex, Object[] objects);
}
