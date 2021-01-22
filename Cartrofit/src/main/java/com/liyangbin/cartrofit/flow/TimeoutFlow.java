package com.liyangbin.cartrofit.flow;

import java.util.TimerTask;
import java.util.function.Consumer;

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
    protected void onSubscribeStarted(Consumer<T> consumer) {
        timeoutConsumer = new TimeoutConsumer(consumer);
        FlowTimer.schedule(timeoutConsumer, timeoutMillis);
        upStream.subscribe(timeoutConsumer);
    }

    @Override
    protected void onSubscribeStopped() {
        timeoutConsumer.safeCancel();
        timeoutConsumer = null;
        upStream.stopSubscribe();
    }

    @Override
    public boolean isHot() {
        return upStream.isHot();
    }

    private class TimeoutConsumer extends TimerTask implements Consumer<T> {
        Consumer<T> downStream;
        boolean expired = true;

        TimeoutConsumer(Consumer<T> downStream) {
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

        synchronized void safeCancel() {
            expired = true;
            cancel();
        }

        @Override
        public void run() {
            stopSubscribe();
            if (timeoutAction != null) {
                timeoutAction.run();
                timeoutAction = null;
            }
        }
    }
}
