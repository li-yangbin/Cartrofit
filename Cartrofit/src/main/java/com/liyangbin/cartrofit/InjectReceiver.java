package com.liyangbin.cartrofit;

public interface InjectReceiver {
    boolean onBeforeInject(CallAdapter.Call injectCall);

    void onAfterInject(CallAdapter.Call injectCall);
}
