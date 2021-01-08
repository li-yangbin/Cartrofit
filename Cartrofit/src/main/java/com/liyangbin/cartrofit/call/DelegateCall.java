package com.liyangbin.cartrofit.call;

import com.liyangbin.cartrofit.Call;
import com.liyangbin.cartrofit.CallAdapter;
import com.liyangbin.cartrofit.CallGroup;
import com.liyangbin.cartrofit.funtion.Union;

public class DelegateCall extends Call {

    private final Call targetCall;

    DelegateCall(Call targetCall) {
        this.targetCall= targetCall.copyByHost(this);
    }

    @Override
    public boolean hasCategory(int category) {
        return targetCall.hasCategory(category);
    }

    @Override
    public boolean hasToken(String token) {
        return targetCall.hasToken(token);
    }

    @Override
    public CallAdapter.FieldAccessible asFieldAccessible() {
        return targetCall.asFieldAccessible();
    }

    @Override
    public Object mapInvoke(Union arg) {
        return targetCall.invoke(arg);
    }
}
