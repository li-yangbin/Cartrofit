package com.android.carretrofit;

public interface InjectReceiver {
    default void onBeforeInject() {
        // ignore
    }

    void onAfterInjected();
}
