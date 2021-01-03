package com.liyangbin.cartrofit;

class StickyFlowImpl extends FlowWrapper implements Flow.StickyFlow<Object> {

    StickyFlowImpl(Flow<?> base) {
        super(base);
    }

    @Override
    public Object get() {
        for (int i = receiverList.size() - 1; i >= 0; i--) {
            Call.OnReceiveCall onReceiveCall = receiverList.get(i);
            Object value = onReceiveCall.getHistoricalData(this);
            if (value != null) {
                return value;
            }
        }
        for (int i = receiverList.size() - 1; i >= 0; i--) {
            Call.OnReceiveCall onReceiveCall = receiverList.get(i);
            Object value = onReceiveCall.loadInitialData();
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
