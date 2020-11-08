package com.android.car.hvac;

import android.app.Application;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.liyangbin.cartrofit.ApiBuilder;
import com.liyangbin.cartrofit.ApiCallback;
import com.liyangbin.cartrofit.Cartrofit;
import com.liyangbin.cartrofit.Command;
import com.liyangbin.cartrofit.CommandType;
import com.liyangbin.cartrofit.Constraint;
import com.liyangbin.cartrofit.Interceptor;

public class HvacApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Cartrofit.builder()
                .addDataSource(new HvacDataSource(this))
                .addInterceptor(new CartrofitLogger())
                .addApiCallback(new ApiCallback() {
                    @Override
                    public void onApiCreate(Class<?> apiClass, ApiBuilder builder) {
                        builder.intercept(new SetCommandDispatcher())
                                .apply(Constraint.of(CommandType.SET));

                        builder.intercept(new ReceiveCommandDispatcher())
                                .apply(Constraint.of(CommandType.RECEIVE));
                    }
                })
                .buildAsDefault();
    }

    private static class CartrofitLogger implements Interceptor {
        @Override
        public Object process(Command command, Object parameter) {
            Log.i("cartrofit", "execute->" + command + " parameter:" + parameter);
            return command.invoke(parameter);
        }
    }

    private static class SetCommandDispatcher implements Interceptor {
        @Override
        public Object process(Command command, Object parameter) {
            AsyncTask.execute(() -> {
                Log.i("cartrofit", "Async execute->" + command + " parameter:" + parameter);
                command.invoke(parameter);
            });
            return null;
        }
    }

    private static class ReceiveCommandDispatcher implements Interceptor {
        Handler mUiHandler = new Handler();

        @Override
        public Object process(Command command, Object parameter) {
            mUiHandler.post(() -> {
                Log.i("cartrofit", "Ui thread receive->" + command + " parameter:" + parameter);
                command.invoke(parameter);
            });
            return null;
        }
    }
}
