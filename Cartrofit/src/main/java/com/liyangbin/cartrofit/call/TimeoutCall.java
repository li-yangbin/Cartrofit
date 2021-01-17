package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.Flow;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.HashMap;
import java.util.TimerTask;
import java.util.function.Consumer;

public class TimeoutCall extends Call {

    private int mTimeoutMillis;

    public TimeoutCall(int timeoutMillis) {
        this.mTimeoutMillis = timeoutMillis;
    }

    @Override
    public Object mapInvoke(Union parameter) {
        return new TimeoutFlow();
    }

    private class TimeoutFlow implements Flow<Void> {

        HashMap<Consumer<Void>, TimerTask> consumers = new HashMap<>();

        @Override
        public void addObserver(Consumer<Void> consumer) {
            TimerTask task = consumers.remove(consumer);
            if (task == null) {
                consumers.put(consumer, task = new TimerTask() {
                    @Override
                    public void run() {
                        consumer.accept(null);
                    }
                });
            }
            sTimeOutTimer.schedule(task, mTimeoutMillis);
        }

        @Override
        public void removeObserver(Consumer<Void> consumer) {
            TimerTask task = consumers.remove(consumer);
            if (task != null) {
                task.cancel();
            }
        }
    }
}
