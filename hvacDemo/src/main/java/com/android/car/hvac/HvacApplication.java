package com.android.car.hvac;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.broadcast.BroadcastContext;
import com.liyangbin.cartrofit.carproperty.context.HvacContext;

public class HvacApplication extends Application {
    private static final String TAG = "HvacApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Cartrofit.register(new HvacContext(new MockHvacAccess(this)));
        Cartrofit.register(new BroadcastContext(this, false));
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

            }
        }, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

}
