package com.liyangbin.cartrofit.support;

public final class SupportUtil {
    private SupportUtil() {
    }

    public static void addSupport() {
        RxJavaConverter.addSupport();
        ObservableConverter.addSupport();
        LiveDataConverter.addSupport();
    }
}
