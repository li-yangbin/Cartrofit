package com.liyangbin.cartrofit.flow;

import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class TimeoutFlow<T> extends Flow<T> {
    private final Flow<T> upStream;
    private final long timeoutMillis;

    public TimeoutFlow(Flow<T> upStream, long timeoutMillis) {
        this.upStream = upStream;
        this.timeoutMillis = timeoutMillis;
        if (timeoutMillis <= 1) {
            throw new RuntimeException("invalid argument:" + timeoutMillis);
        }
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        TimeoutConsumer timeoutConsumer = new TimeoutConsumer(consumer);
        FlowTimer.schedule(timeoutConsumer, timeoutMillis);
        upStream.subscribe(timeoutConsumer);
    }

    @Override
    protected void onSubscribeStopped() {
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class TimeoutConsumer extends TimerTask implements FlowConsumer<T> {
        FlowConsumer<T> downStream;
        volatile boolean expired = true;

        TimeoutConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            if (expired) {
                return;
            }
            downStream.accept(t);
        }

        @Override
        public void run() {
            if (expired) {
                return;
            }
            expired = true;
            upStream.stopSubscribe();
            TimeoutException timeoutException = new TimeoutException();
            downStream.onError(timeoutException);
        }

        @Override
        public void onCancel() {
            if (expired) {
                return;
            }
            expired = true;
            cancel();
            downStream.onCancel();
        }

        @Override
        public void onComplete() {
            if (expired) {
                return;
            }
            expired = true;
            cancel();
            downStream.onComplete();
        }

        @Override
        public void onError(Throwable throwable) {
            if (expired) {
                return;
            }
            expired = true;
            downStream.onError(throwable);
        }
    }

}
