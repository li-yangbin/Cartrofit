package com.liyangbin.cartrofit.flow;

public interface FlowSource<T> {
    void startWithInjector(Flow.Injector<T> injector);
    void finishWithInjector(Flow.Injector<T> injector);

    default boolean isHot() {
        return true;
    }
}
