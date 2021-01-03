package com.liyangbin.cartrofit;

public interface InjectReceiver {
    boolean onBeforeInject(Call injectCall);

    void onAfterInject(Call injectCall);
}
