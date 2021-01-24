package com.liyangbin.cartrofit.flow;

import java.util.Timer;
import java.util.TimerTask;

class FlowTimer {
    private static final Timer TIMER = new Timer("flow-timer");

    FlowTimer() {
    }

    static void schedule(TimerTask task, long delay) {
        TIMER.schedule(task, delay);
    }

    static void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        TIMER.scheduleAtFixedRate(task, delay, period);
    }
}
