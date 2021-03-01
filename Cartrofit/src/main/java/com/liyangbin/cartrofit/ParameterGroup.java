package com.liyangbin.cartrofit;

public interface ParameterGroup {
    boolean isTaken(Call call);
    int getParameterCount();
    Parameter getParameterAt(int index);
    Key getDeclaredKey();
}
