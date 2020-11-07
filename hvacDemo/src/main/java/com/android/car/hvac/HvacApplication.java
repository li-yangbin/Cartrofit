package com.android.car.hvac;

import android.app.Application;
import android.util.Log;

import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.Command;
import com.liyangbin.cartrofit.Interceptor;

public class HvacApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Cartrofit.builder()
                .addDataSource(new HvacDataSource(this))
                .addInterceptor(new CartrofitLogger())
                .buildAsDefault();
    }

    private static class CartrofitLogger implements Interceptor {
        @Override
        public Object process(Command command, Object parameter) {
            Log.i("cartrofit", "execute->" + command + " parameter:" + parameter);
            return command.invoke(parameter);
        }
    }
}
