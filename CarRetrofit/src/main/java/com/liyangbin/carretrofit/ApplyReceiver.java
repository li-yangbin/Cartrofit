package com.liyangbin.carretrofit;

public interface ApplyReceiver {
    void onBeforeApply();

    default void onAfterApplied() {
        // ignore
    }
}
