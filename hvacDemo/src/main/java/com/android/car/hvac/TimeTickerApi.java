package com.android.car.hvac;

import android.content.Intent;

import com.liyangbin.cartrofit.annotation.GenerateId;
import com.liyangbin.cartrofit.annotation.Register;
import com.liyangbin.cartrofit.annotation.Unregister;
import com.liyangbin.cartrofit.broadcast.Broadcast;
import com.liyangbin.cartrofit.broadcast.Receive;

@Broadcast
@GenerateId
public interface TimeTickerApi {
    @Register
    void registerScreenOffListener(OnOffListener listener);

    @Unregister(TimeTickerApiId.registerScreenOffListener)
    void unregisterScreenOffListener(OnOffListener listener);

    @Receive(action = Intent.ACTION_TIME_TICK)
    void registerTimeTickListener(Runnable action);

    @Register
    void registerTimeChangeListener(TimeChangeListener listener);

    @Unregister(TimeTickerApiId.registerTimeChangeListener)
    void unregisterTimeChangeListener(TimeChangeListener listener);
}
