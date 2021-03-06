package com.liyangbin.cartrofit.carproperty;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.OnError;

public interface DummyApi {

    @Delegate(TestCarApiId.getIntSignal)
    int getDummyIntSignal();

    @Delegate(TestCarApiId.setIntSignal)
    void setDummyIntSignal(int value);

    @Delegate(TestCarApiId.registerStringChangeListenerAlias)
    void registerDummyStringChangeListenerAlias(/*@Callback */DummyListener listener);

    interface DummyListener {
        @Callback
        void onChange(String value);

        @OnError
        default void onError(Throwable throwable) {
        }
    }
}
