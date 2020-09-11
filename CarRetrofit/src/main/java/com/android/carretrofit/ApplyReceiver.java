package com.android.carretrofit;

public interface ApplyReceiver {
    void onBeforeApply();

    default void onAfterApplied() {
        // ignore
    }
}
