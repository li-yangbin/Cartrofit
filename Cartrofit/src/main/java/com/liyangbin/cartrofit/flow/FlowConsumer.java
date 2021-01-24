package com.liyangbin.cartrofit.flow;

import java.util.function.Consumer;

public interface FlowConsumer<T> extends Consumer<T> {
    default void onComplete() {
    }
}
