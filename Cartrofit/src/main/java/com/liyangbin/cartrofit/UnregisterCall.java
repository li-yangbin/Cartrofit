package com.liyangbin.cartrofit;

import java.util.Objects;

public class UnregisterCall extends Call {
    private final RegisterCall trackCall;

    public UnregisterCall(RegisterCall trackCall) {
        this.trackCall = trackCall;
    }

    @Override
    public Object invoke(Object[] parameter) {
        trackCall.untrack(Objects.requireNonNull(parameter[0]));
        return null;
    }
}
