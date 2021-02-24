package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.funtion.Union;

import java.util.Objects;

public class UnregisterCall extends Call {
    private final RegisterCall trackCall;

    public UnregisterCall(RegisterCall trackCall) {
        this.trackCall = trackCall;
    }

    @Override
    public Object mapInvoke(Union parameter) {
        trackCall.untrack(Objects.requireNonNull(parameter.get(0)));
        return null;
    }
}
