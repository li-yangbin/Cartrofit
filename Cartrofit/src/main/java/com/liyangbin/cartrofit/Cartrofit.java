package com.liyangbin.cartrofit;

public final class Cartrofit {

    public static <T> T from(Class<T> api) {
        return RootContext.getInstance().from(api);
    }
}
