package com.android.car.hvac;

import android.content.Intent;

import com.liyangbin.cartrofit.annotation.Callback;
import com.liyangbin.cartrofit.annotation.Process;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Unregister;
import com.liyangbin.cartrofit.broadcast.Broadcast;
import com.liyangbin.cartrofit.broadcast.Receive;

@Broadcast
@Process
public interface TimeTickerApi {
    @Register
    void registerScreenOffListener(@Callback OnOffListener listener);

    @Unregister(TimeTickerApiId.registerScreenOffListener)
    void unregisterScreenOffListener(OnOffListener listener);

    @Receive(action = Intent.ACTION_TIME_TICK)
    void registerTimeTickListener(@Callback Runnable action);

    @Register
    void registerTimeChangeListener(@Callback TimeChangeListener listener);

    @Unregister(TimeTickerApiId.registerTimeChangeListener)
    void unregisterTimeChangeListener(TimeChangeListener listener);
}
