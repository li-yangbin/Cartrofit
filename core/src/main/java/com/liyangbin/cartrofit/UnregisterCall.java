package com.liyangbin.cartrofit;

import java.util.Objects;

public class UnregisterCall extends Call {
    private final RegisterCall trackCall;

    UnregisterCall(RegisterCall trackCall) {
        this.trackCall = trackCall;
    }

    @Override
    public Object invoke(Object[] parameter) {
        Object callback = Objects.requireNonNull(parameter[0]);
        trackCall.untrack(callback);
        return null;
    }
}
