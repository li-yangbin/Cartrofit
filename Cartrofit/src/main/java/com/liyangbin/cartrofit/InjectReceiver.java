package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.call.Call;

public interface InjectReceiver {
    boolean onBeforeInject(Call injectCall);

    void onAfterInject(Call injectCall);
}
