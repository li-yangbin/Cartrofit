package com.liyangbin.cartrofit;

public class CommandImpl extends CommandFlow {

    @Override
    void onInit(CallAdapter<?, ?>.Call call) {
        super.onInit(call);
    }

    @Override
    Object doInvoke(Object parameter) {
        return call.invoke(parameter);
    }

    @Override
    public CommandType getType() {
        return null;
    }
}
