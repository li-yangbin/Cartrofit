package com.liyangbin.carretrofit;

public interface InjectReceiver {
    default void onBeforeInject() {
        // ignore
    }

    void onAfterInjected();
}
