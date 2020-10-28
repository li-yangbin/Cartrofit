package com.liyangbin.carretrofit;

import com.liyangbin.carretrofit.annotation.Delegate;

public interface MyCallback {

    @Delegate(TestCarApiId.trackStringSignal)
    void onStringChange(String aa);
}
