package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Delegate;
import com.liyangbin.cartrofit.annotation.Scope;

import io.reactivex.Observable;

@Scope(value = "test", publish = true)
public interface TestChildCarApi {

//    @Delegate(getIntSignal)
    int getIntValueAlias();

    @Delegate(TestCarApiId.trackIntReactive)
    Observable<Integer> trackIntReactiveAlias();
}
