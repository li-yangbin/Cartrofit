package com.liyangbin.cartrofit.flow;

import java.util.TimerTask;

public class TimeoutFlow<T> extends Flow<T> {
    private final Flow<T> upStream;
    private final long timeoutMillis;
    private TimeoutConsumer timeoutConsumer;
    private Runnable timeoutAction;

    public TimeoutFlow(Flow<T> upStream, long timeoutMillis, Runnable timeout) {
        this.upStream = upStream;
        this.timeoutMillis = timeoutMillis;
        if (timeoutMillis <= 1) {
            throw new RuntimeException("invalid argument:" + timeoutMillis);
        }
        this.timeoutAction = timeout;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<T> consumer) {
        timeoutConsumer = new TimeoutConsumer(consumer);
        FlowTimer.schedule(timeoutConsumer, timeoutMillis);
        upStream.subscribe(timeoutConsumer);
    }

    @Override
    protected void onSubscribeStopped() {
        timeoutConsumer.expire();
        timeoutConsumer = null;
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class TimeoutConsumer extends TimerTask implements FlowConsumer<T> {
        FlowConsumer<T> downStream;
        boolean expired = true;

        TimeoutConsumer(FlowConsumer<T> downStream) {
            this.downStream = downStream;
        }

        @Override
        public void accept(T t) {
            synchronized (this) {
                if (expired) {
                    return;
                }
            }
            downStream.accept(t);
        }

        synchronized void expire() {
            expired = true;
            cancel();
        }

        @Override
        public void run() {
            synchronized (this) {
                if (expired) {
                    return;
                }
                expired = true;
            }
            upStream.stopSubscribe();
            if (timeoutAction != null) {
                timeoutAction.run();
                timeoutAction = null;
            }
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (expired) {
                    return;
                }
                expired = true;
            }
            cancel();
            downStream.onComplete();
        }
    }
}
