package com.liyangbin.cartrofit;

public class UnregisterCall extends CallAdapter.Call {
    RegisterCall trackCall;

    @Override
    public void onInit(ConverterFactory scopeFactory) {
        if (key.method != null && key.method.getReturnType() == void.class) {
            Class<?>[] classArray = key.method.getParameterTypes();
            if (classArray.length == 1) {
                if (classArray[0] == trackCall.getMethod().getParameterTypes()[0]) {
                    return;
                }
            }
        }
        throw new CartrofitGrammarException("invalid unTrack:" + this);
    }

    void setRegisterCall(RegisterCall trackCall) {
        this.trackCall = trackCall;
    }

    @Override
    public Object doInvoke(Object callback) {
        trackCall.untrack(callback);
        return null;
    }
}
