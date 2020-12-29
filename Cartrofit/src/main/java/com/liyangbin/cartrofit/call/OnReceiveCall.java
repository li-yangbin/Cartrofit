package com.liyangbin.cartrofit.call;

import java.util.Map;
import java.util.WeakHashMap;

public class OnReceiveCall extends Call {

    private final WeakHashMap<FlowWrapper, Object> lastTransactData = new WeakHashMap<>();
    private boolean keepLatestData;

    private final Call trackCall;

    OnReceiveCall(Call trackCall) {
        this.trackCall = trackCall;
    }

    @Override
    Object mapInvoke(Object parameter) {
        return null;
    }

    @Override
    protected Object doInvoke(Object arg) {
        return null;
    }

    void enableSaveLatestData() {
        keepLatestData = true;
    }

    void restoreDispatch() {
        keepLatestData = false;
        for (Map.Entry<FlowWrapper, Object> entry : lastTransactData.entrySet()) {
            invokeWithFlow(entry.getKey(), entry.getValue());
        }
        keepLatestData = true;
    }

    boolean hasHistoricalData() {
        return !lastTransactData.isEmpty();
    }

    Object getHistoricalData(FlowWrapper flowWrapper) {
        return lastTransactData.get(flowWrapper);
    }

    Object loadInitialData() {
        return trackCall.isStickyTrackEnable() ? trackCall.onLoadStickyValue() : null;
    }

    final void invokeWithFlow(FlowWrapper callFrom, Object transact) {
        if (interceptorChain != null) {
            interceptorChain.doProcess(new OnReceiveCallSession(callFrom), transact);
        } else {
            if (keepLatestData) {
                lastTransactData.put(callFrom, transact);
            }
            callFrom.onReceiveComplete(OnReceiveCall.this, transact);
        }
    }

    private class OnReceiveCallSession extends Interceptor.InvokeSession {

        final FlowWrapper callFrom;

        OnReceiveCallSession(FlowWrapper callFrom) {
            super(trackCall);
            this.callFrom = callFrom;
        }

        @Override
        public void onInterceptComplete(Object transact) {
            if (keepLatestData) {
                lastTransactData.put(callFrom, transact);
            }
            this.callFrom.onReceiveComplete(OnReceiveCall.this, transact);
        }

        @Override
        public boolean isReceive() {
            return true;
        }
    }
}
