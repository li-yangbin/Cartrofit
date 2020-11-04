package com.liyangbin.cartrofit;

public interface InjectReceiver {
    boolean onBeforeInject(Command injectCommand);

    void onAfterInject(Command injectCommand);
}
