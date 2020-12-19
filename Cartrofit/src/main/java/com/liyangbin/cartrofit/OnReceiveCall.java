package com.liyangbin.cartrofit;

import java.util.Map;
import java.util.WeakHashMap;

public class OnReceiveCall extends CallAdapter.Call {

    private final WeakHashMap<FlowWrapper<Object>, Object> lastTransactData = new WeakHashMap<>();
    private boolean restoreDataEnable;

    @Override
    protected Object doInvoke(Object arg) {
        return null;
    }

    void enableRestoreData() {
        restoreDataEnable = true;
    }

    void restoreDispatch() {
        restoreDataEnable = false;
        for (Map.Entry<FlowWrapper<Object>, Object> entry : lastTransactData.entrySet()) {
            invokeWithFlow(entry.getKey(), entry.getValue());
        }
        restoreDataEnable = true;
    }

    boolean hasHistoricalData() {
        return !lastTransactData.isEmpty();
    }

    final void invokeWithFlow(FlowWrapper<Object> callFrom, Object transact) {
        if (interceptorChain != null) {
            interceptorChain.doProcess(new OnReceiveCallSession(callFrom, this), transact);
        } else {
            callFrom.onReceiveComplete(OnReceiveCall.this, transact);
        }
    }

    private class OnReceiveCallSession extends Interceptor.InvokeSession {

        FlowWrapper<Object> callFrom;

        OnReceiveCallSession(FlowWrapper<Object> callFrom, CallAdapter.Call call) {
            super(call);
            this.callFrom = callFrom;
        }

        @Override
        public void onInterceptComplete(Object transact) {
            if (restoreDataEnable) {
                lastTransactData.put(callFrom, transact);
            }
            this.callFrom.onReceiveComplete(OnReceiveCall.this, transact);
        }
    }
}
