package com.liyangbin.cartrofit.solution;

import com.liyangbin.cartrofit.Parameter;

public interface ParaVal<V> {
    Parameter getParameter();
    V get();
    void set(V value);
}
