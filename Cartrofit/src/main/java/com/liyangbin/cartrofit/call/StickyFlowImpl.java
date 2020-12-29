package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Flow;

public class StickyFlowImpl extends FlowWrapper implements Flow.StickyFlow<Object> {

    public StickyFlowImpl(Flow<?> base) {
        super(base);
    }

    @Override
    public Object get() {
        for (int i = receiverList.size() - 1; i >= 0; i--) {
            OnReceiveCall onReceiveCall = receiverList.get(i);
            Object value = onReceiveCall.getHistoricalData(this);
            if (value != null) {
                return value;
            }
        }
        for (int i = receiverList.size() - 1; i >= 0; i--) {
            OnReceiveCall onReceiveCall = receiverList.get(i);
            Object value = onReceiveCall.loadInitialData();
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
