package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CartrofitGrammarException;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.Objects;

public class UnregisterCall extends Call {
    private final RegisterCall trackCall;

    @Override
    public void onInit() {
        if (getKey().method != null && getKey().method.getReturnType() == void.class) {
            Class<?>[] classArray = getKey().method.getParameterTypes();
            if (classArray.length == 1) {
                if (classArray[0] == trackCall.getMethod().getParameterTypes()[0]) {
                    return;
                }
            }
        }
        throw new CartrofitGrammarException("invalid unTrack:" + this);
    }

    public UnregisterCall(RegisterCall trackCall) {
        this.trackCall = trackCall;
    }

    @Override
    public Object mapInvoke(Union parameter) {
        trackCall.untrack(Objects.requireNonNull(parameter.get(0)));
        return null;
    }
}
