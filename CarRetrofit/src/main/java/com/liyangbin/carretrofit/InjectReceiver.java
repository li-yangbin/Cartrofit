package com.liyangbin.carretrofit;

public interface InjectReceiver {
    boolean onBeforeInject(Command injectCommand);

    void onAfterInject(Command injectCommand);
}
