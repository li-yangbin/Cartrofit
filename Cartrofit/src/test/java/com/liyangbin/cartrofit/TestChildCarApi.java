package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.CarApi;
import com.liyangbin.cartrofit.annotation.Delegate;

import io.reactivex.Observable;

@CarApi
public interface TestChildCarApi {

//    @Delegate(getIntSignal)
    int getIntValueAlias();

    @Delegate(TestCarApiId.trackIntReactive)
    Observable<Integer> trackIntReactiveAlias();
}
