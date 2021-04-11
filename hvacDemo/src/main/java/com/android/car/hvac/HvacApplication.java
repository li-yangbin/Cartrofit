package com.android.car.hvac;

import android.app.Application;
import android.util.Log;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.broadcast.BroadcastContext;
import com.liyangbin.cartrofit.carproperty.context.HvacContext;

public class HvacApplication extends Application implements OnOffListener {
    private static final String TAG = "HvacApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        HvacContext hvacContext = new HvacContext(new MockHvacAccess(this));
        hvacContext.setDebounceMillis(0);
        Cartrofit.register(hvacContext);
        Cartrofit.register(new BroadcastContext(this, false));
        Cartrofit.from(TimeTickerApi.class).registerScreenOffListener(this);
    }

    @Override
    public void onScreenOff() {
        Log.i(TAG, "onScreenOff");
    }

    @Override
    public void onScreenOn() {
        Log.i(TAG, "onScreenOn");
    }
}
