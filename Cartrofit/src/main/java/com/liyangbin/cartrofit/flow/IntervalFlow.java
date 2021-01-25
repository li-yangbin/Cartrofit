package com.liyangbin.cartrofit.flow;

import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IntervalFlow extends Flow<Integer> {

    private final int startDelay;
    private final int interval;
    private ScheduleTask task;

    public IntervalFlow(int startDelay, int interval) {
        this.startDelay = startDelay;
        this.interval = interval;
    }

    @Override
    protected void onSubscribeStarted(FlowConsumer<Integer> consumer) {
        task = new ScheduleTask(consumer);
        if (interval == 0) {
            FlowTimer.schedule(task, startDelay);
        } else {
            if (startDelay == 0) {
                FlowTimer.scheduleAtFixedRate(task, interval, interval);
            } else {
                FlowTimer.scheduleAtFixedRate(task, startDelay, interval);
            }
        }
    }

    @Override
    public boolean isHot() {
        return false;
    }

    @Override
    protected void onSubscribeStopped() {
        task.safeCancel();
        task = null;
    }

    private static class ScheduleTask extends TimerTask {
        FlowConsumer<Integer> consumer;
        AtomicInteger indexRef = new AtomicInteger();
        AtomicBoolean expiredRef = new AtomicBoolean();

        ScheduleTask(FlowConsumer<Integer> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void run() {
            if (expiredRef.get()) {
                return;
            }
            consumer.accept(indexRef.getAndIncrement());
        }

        void safeCancel() {
            expiredRef.set(true);
            cancel();
        }
    }
}
