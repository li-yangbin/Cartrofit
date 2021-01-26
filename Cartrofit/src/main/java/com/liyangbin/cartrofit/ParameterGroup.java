package com.liyangbin.cartrofit;

public interface ParameterGroup {
    String token();
    int getParameterCount();
    Parameter getParameterAt(int index);
    Key getDeclaredKey();
}
